#include <jni.h>
#include <pthread.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>
#include <errno.h>
#include <string.h>
#include <stdlib.h>
#include <stdbool.h>
#include <android/log.h>
#include <poll.h>
#include <sys/time.h>
#include "include/pcapd.h"

#define LOG_TAG "SocketJNI"
#define log_e(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define log_w(...) __android_log_print(ANDROID_LOG_WARN,  LOG_TAG, __VA_ARGS__)
#define log_i(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define log_d(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)


#define SOCKET_READ_BUFFER_SIZE 4096
#define JNI_BRIDGE_CLASS_NAME "com/packet/analyzer/data/datasource/native/JniBridge"
#define ON_PACKET_CALLBACK_NAME "onPacketHeaderReceivedCallback"
// Сигнатура: (int uid, long totalLen, long ipLen, int proto, boolean isUl, long tsSec, long tsUsec, int drops, int ifId)
#define ON_PACKET_CALLBACK_SIG "(IJJI Z JJII)V"
#define IP_HEADER_MIN_READ 20


static int server_sock = -1;
static int client_sock = -1;
static pthread_t reader_thread_id = 0;
static volatile bool keep_reading = false;
static jclass g_jniBridgeClassRef = NULL;
static jmethodID g_onPacketCallbackMethodID = NULL;
static JavaVM *g_javaVM = NULL;


JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    g_javaVM = vm;
    log_i("JNI_OnLoad completed.");
    return JNI_VERSION_1_6;
}



static int read_exact(int sock_fd, void *buf, size_t count) {
    size_t bytes_read = 0;
    char *ptr = (char*)buf;
    if (sock_fd < 0) return -1;

    while (bytes_read < count && keep_reading) {
        ssize_t result = read(sock_fd, ptr + bytes_read, count - bytes_read);
        if (result < 0) {
            if (errno == EINTR || errno == EAGAIN || errno == EWOULDBLOCK) {
                usleep(1000); continue;
            }
            log_e("read_exact: read error on fd %d: %s (errno %d)", sock_fd, strerror(errno), errno);
            return -1;
        } else if (result == 0) {
            log_w("read_exact: socket closed (EOF) on fd %d while reading %zu bytes (read %zu)", sock_fd, count, bytes_read);
            return -1;
        }
        bytes_read += result;
    }
    if (!keep_reading && bytes_read < count) return 1;
    return 0;
}

static int skip_exact(int sock_fd, size_t count) {
    char skip_buffer[SOCKET_READ_BUFFER_SIZE];
    size_t bytes_skipped = 0;
    if (sock_fd < 0) return -1;

    while (bytes_skipped < count && keep_reading) {
        size_t to_read = count - bytes_skipped;
        if (to_read > sizeof(skip_buffer)) to_read = sizeof(skip_buffer);
        ssize_t result = read(sock_fd, skip_buffer, to_read);
        if (result < 0) {
            if (errno == EINTR || errno == EAGAIN || errno == EWOULDBLOCK) {
                usleep(1000); continue;
            }
            log_e("skip_exact: read error on fd %d: %s (errno %d)", sock_fd, strerror(errno), errno);
            return -1;
        } else if (result == 0) {
            log_w("skip_exact: socket closed (EOF) on fd %d while skipping %zu bytes (skipped %zu)", sock_fd, count, bytes_skipped);
            return -1;
        }
        bytes_skipped += result;
    }
    if (!keep_reading && bytes_skipped < count) return 1;
    return 0;
}


static int get_ip_offset(int linktype) {
    switch(linktype) {
        case PCAPD_DLT_RAW: return 0;
        case PCAPD_DLT_ETHERNET: return 14;
        case PCAPD_DLT_LINUX_SLL: return 16;
        case PCAPD_DLT_LINUX_SLL2: return 20;
        default:
            log_w("get_ip_offset: Unknown link type %d, assuming offset 0", linktype);
            return 0;
    }
}


void* reader_thread_func(void* arg) {
    JNIEnv* env = NULL;
    bool attached = false;
    int current_client_sock = -1;
    long packet_count = 0;


    if ((*g_javaVM)->GetEnv(g_javaVM, (void**)&env, JNI_VERSION_1_6) != JNI_OK) {
        if ((*g_javaVM)->AttachCurrentThread(g_javaVM, &env, NULL) != JNI_OK) {
            log_e("ReaderThread: Failed to attach thread to JVM.");
            goto cleanup_thread;
        }
        attached = true;
        log_i("ReaderThread: Attached to JVM.");
    } else {
        log_i("ReaderThread: Already attached to JVM.");
    }

    if (g_jniBridgeClassRef == NULL || g_onPacketCallbackMethodID == NULL) {
        log_e("ReaderThread: JNI callback references not initialized!");
        goto cleanup_thread;
    }


    if (server_sock < 0) {
        log_e("ReaderThread: Server socket is not valid!");
        goto cleanup_thread;
    }
    log_i("ReaderThread: Waiting for client connection on server socket %d...", server_sock);
    struct sockaddr_un client_addr;
    socklen_t client_len = sizeof(client_addr);
    current_client_sock = accept(server_sock, (struct sockaddr*)&client_addr, &client_len);

    if (current_client_sock < 0) {
        if (keep_reading) log_e("ReaderThread: Failed to accept client connection: %s", strerror(errno));
        else log_i("ReaderThread: Accept interrupted.");
        goto cleanup_thread;
    }


    __sync_val_compare_and_swap(&client_sock, -1, current_client_sock);
    log_i("ReaderThread: Client connected (socket: %d). Starting read loop.", client_sock);



    struct pollfd fds[1];
    fds[0].fd = client_sock;
    fds[0].events = POLLIN;

    while (keep_reading && client_sock >= 0) {
        int poll_ret = poll(fds, 1, 1000);

        if (poll_ret < 0) {
            if (errno != EINTR) { log_e("ReaderThread: poll() error: %s", strerror(errno)); break; }
            continue;
        } else if (poll_ret == 0) {
            continue;
        }

        if (fds[0].revents & (POLLERR | POLLHUP | POLLNVAL)) {
            log_e("ReaderThread: Socket error detected by poll().");
            break;
        }
        if (!(fds[0].revents & POLLIN)) {
            continue;
        }

        pcapd_hdr_t hdr;
        int read_result = read_exact(client_sock, &hdr, sizeof(hdr));
        if (read_result != 0) {
            if (read_result == 1) log_i("ReaderThread: Header read interrupted.");
            else log_e("ReaderThread: Header read failed or socket closed.");
            break;
        }


        if (hdr.len > PCAPD_SNAPLEN || hdr.len < 0) {
            log_e("ReaderThread: Invalid packet length in header: %u. Skipping.", hdr.len);
            if (skip_exact(client_sock, hdr.len) != 0) { log_e("ReaderThread: Error skipping invalid packet."); break; }
            continue;
        }
        if (hdr.len == 0) { continue; }


        int ip_offset = get_ip_offset(hdr.linktype);
        long ip_packet_size = 0;
        if (hdr.len >= ip_offset) {
            ip_packet_size = (long)hdr.len - ip_offset;
        } else {
            log_w("ReaderThread: hdr.len (%u) < ip_offset (%d), reporting size 0.", hdr.len, ip_offset);
            ip_packet_size = 0;
            if (skip_exact(client_sock, hdr.len) != 0) { break; }
            continue;
        }


        if (ip_offset > 0) {
            if (skip_exact(client_sock, ip_offset) != 0) break;
        }


        int protocol_code = 0;
        char ip_header_buffer[IP_HEADER_MIN_READ];
        ssize_t ip_header_len_to_read = (ip_packet_size >= IP_HEADER_MIN_READ) ? IP_HEADER_MIN_READ : ip_packet_size;
        ssize_t ip_read_actual = 0;

        if (ip_header_len_to_read > 0) {
            if (read_exact(client_sock, ip_header_buffer, ip_header_len_to_read) == 0) {
                ip_read_actual = ip_header_len_to_read;
                int ip_version = (ip_header_buffer[0] >> 4) & 0x0F;
                if (ip_version == 4 && ip_read_actual >= 10) protocol_code = (unsigned char)ip_header_buffer[9];
                else if (ip_version == 6 && ip_read_actual >= 7) protocol_code = (unsigned char)ip_header_buffer[6];
            } else break;
        }


        ssize_t bytes_to_skip = ip_packet_size - ip_read_actual;
        if (bytes_to_skip > 0) {
            if (skip_exact(client_sock, bytes_to_skip) != 0) break;
        } else if (bytes_to_skip < 0) {
            log_e("ReaderThread: Negative bytes_to_skip! Calculation error!");
            break;
        }


        jboolean is_uplink = (hdr.flags & PCAPD_FLAG_TX) != 0;


        packet_count++;
        (*env)->CallStaticVoidMethod(env, g_jniBridgeClassRef, g_onPacketCallbackMethodID,
                                     (jint)hdr.uid,         // UID
                                     (jlong)hdr.len,        // Total Length (Frame size)
                                     (jlong)ip_packet_size, // IP Packet Size
                                     (jint)protocol_code,   // Protocol (TCP/UDP/ICMP code)
                                     is_uplink,             // Direction (true if Uplink/TX)
                                     (jlong)hdr.ts.tv_sec,  // Timestamp seconds
                                     (jlong)hdr.ts.tv_usec, // Timestamp microseconds
                                     (jint)hdr.pkt_drops,   // Packet drops
                                     (jint)hdr.ifid);       // Interface ID
        if ((*env)->ExceptionCheck(env)) {
            log_e("ReaderThread: *** Exception occurred calling back to Kotlin! (Packet %ld) ***", packet_count);
            (*env)->ExceptionDescribe(env);
            (*env)->ExceptionClear(env);

        }

    }

    cleanup_thread:
    log_i("ReaderThread: Exiting loop. Processed %ld packets.", packet_count);
    if (current_client_sock >= 0) {
        log_d("ReaderThread: Closing client socket %d in cleanup", current_client_sock);
        close(current_client_sock);

        __sync_bool_compare_and_swap(&client_sock, current_client_sock, -1);
    } else if (client_sock >= 0) {
        log_w("ReaderThread: Cleaning up potentially dangling global client_sock %d", client_sock);
        __sync_bool_compare_and_swap(&client_sock, client_sock, -1);
    }

    if (attached) {
        (*g_javaVM)->DetachCurrentThread(g_javaVM);
        log_i("ReaderThread: Detached from JVM.");
    }
    log_i("ReaderThread: Thread finished.");
    return NULL;
}



JNIEXPORT jboolean JNICALL
Java_com_packet_analyzer_data_datasource_native_JniBridge_nativeStartSocketListener(JNIEnv *env, jobject thiz, jstring socketPath) {
    if (server_sock >= 0 || reader_thread_id != 0) {
        log_w("nativeStartSocketListener: Listener already seems active (socket %d, thread %ld).", server_sock, (long)reader_thread_id);
        return JNI_TRUE;
    }
    if (g_javaVM == NULL) {
        log_e("nativeStartSocketListener: JavaVM is NULL! JNI_OnLoad failed or not called?");
        return JNI_FALSE;
    }

    log_i("nativeStartSocketListener: Initializing...");


    if (g_jniBridgeClassRef == NULL) {
        log_d("nativeStartSocketListener: Initializing JNI references...");
        jclass localBridgeClass = (*env)->FindClass(env, JNI_BRIDGE_CLASS_NAME);
        if (localBridgeClass == NULL) { log_e("nativeStartSocketListener: FindClass failed: %s", JNI_BRIDGE_CLASS_NAME); return JNI_FALSE; }
        g_jniBridgeClassRef = (*env)->NewGlobalRef(env, localBridgeClass);
        (*env)->DeleteLocalRef(env, localBridgeClass);
        if (g_jniBridgeClassRef == NULL) { log_e("nativeStartSocketListener: NewGlobalRef failed"); return JNI_FALSE; }

        g_onPacketCallbackMethodID = (*env)->GetStaticMethodID(env, g_jniBridgeClassRef, ON_PACKET_CALLBACK_NAME,
                                                               "(IJJIZJJII)V");
        if (g_onPacketCallbackMethodID == NULL) {
            log_e("nativeStartSocketListener: GetStaticMethodID failed: %s%s", ON_PACKET_CALLBACK_NAME, ON_PACKET_CALLBACK_SIG);
            (*env)->DeleteGlobalRef(env, g_jniBridgeClassRef); g_jniBridgeClassRef = NULL;
            return JNI_FALSE;
        }
        log_d("nativeStartSocketListener: JNI references initialized successfully.");
    } else {
        log_d("nativeStartSocketListener: JNI references already initialized.");
    }


    const char *path = (*env)->GetStringUTFChars(env, socketPath, 0);
    if (path == NULL) { log_e("nativeStartSocketListener: GetStringUTFChars failed"); return JNI_FALSE; }
    char local_socket_path[128];
    strncpy(local_socket_path, path, sizeof(local_socket_path) - 1);
    local_socket_path[sizeof(local_socket_path) - 1] = '\0';
    (*env)->ReleaseStringUTFChars(env, socketPath, path);

    log_i("nativeStartSocketListener: Preparing socket at %s", local_socket_path);

    server_sock = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
    if (server_sock < 0) { log_e("nativeStartSocketListener: socket() failed: %s", strerror(errno)); return JNI_FALSE; }

    int reuse = 1;
    setsockopt(server_sock, SOL_SOCKET, SO_REUSEADDR, (const char*)&reuse, sizeof(reuse));

    struct sockaddr_un addr;
    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;
    strncpy(addr.sun_path, local_socket_path, sizeof(addr.sun_path) - 1);
    unlink(local_socket_path);

    if (bind(server_sock, (struct sockaddr*)&addr, sizeof(addr)) < 0) {
        log_e("nativeStartSocketListener: bind() failed for %s: %s", local_socket_path, strerror(errno));
        close(server_sock); server_sock = -1;
        return JNI_FALSE;
    }

    if (listen(server_sock, 1) < 0) {
        log_e("nativeStartSocketListener: listen() failed: %s", strerror(errno));
        close(server_sock); server_sock = -1; unlink(local_socket_path);
        return JNI_FALSE;
    }

    log_i("nativeStartSocketListener: Socket created and listening (fd=%d).", server_sock);


    keep_reading = true;
    client_sock = -1;
    int pthread_create_result = pthread_create(&reader_thread_id, NULL, reader_thread_func, NULL);
    if (pthread_create_result != 0) {
        log_e("nativeStartSocketListener: Failed to create reader thread: %s", strerror(pthread_create_result));
        close(server_sock); server_sock = -1; unlink(local_socket_path);
        reader_thread_id = 0;
        return JNI_FALSE;
    }

    log_i("nativeStartSocketListener: Listener initialization complete, reader thread created (ID: %ld).", (long)reader_thread_id);
    return JNI_TRUE;
}


JNIEXPORT void JNICALL
Java_com_packet_analyzer_data_datasource_native_JniBridge_nativeStopSocketListener(JNIEnv *env, jobject thiz) {
    log_i("nativeStopSocketListener: Stopping listener...");
    if (!keep_reading && server_sock < 0 && client_sock < 0 && reader_thread_id == 0) {
        log_i("nativeStopSocketListener: Listener already stopped.");
        return;
    }

    keep_reading = false;


    int temp_client_sock = __sync_val_compare_and_swap(&client_sock, client_sock, -1);
    int temp_server_sock = __sync_val_compare_and_swap(&server_sock, server_sock, -1);


    if (temp_client_sock >= 0) {
        log_d("nativeStopSocketListener: Shutting down client socket %d", temp_client_sock);
        shutdown(temp_client_sock, SHUT_RDWR);
        close(temp_client_sock);
    }
    if (temp_server_sock >= 0) {
        log_d("nativeStopSocketListener: Shutting down server socket %d", temp_server_sock);
        shutdown(temp_server_sock, SHUT_RDWR);
        close(temp_server_sock);
    }


    pthread_t current_thread_id = __sync_val_compare_and_swap(&reader_thread_id, reader_thread_id, 0);
    if (current_thread_id != 0) {
        log_d("nativeStopSocketListener: Joining reader thread %ld...", (long)current_thread_id);
        int join_res = pthread_join(current_thread_id, NULL);
        if (join_res == 0) log_d("nativeStopSocketListener: Reader thread joined successfully.");
        else log_e("nativeStopSocketListener: Error joining reader thread %ld: %s", (long)current_thread_id, strerror(join_res));
    } else {
        log_d("nativeStopSocketListener: Reader thread was not active or already joined.");
    }


    log_i("nativeStopSocketListener: Listener stopped completely.");
}


JNIEXPORT void JNICALL
Java_com_packet_analyzer_data_datasource_native_JniBridge_nativeCleanup(JNIEnv *env, jobject thiz) {
    log_i("nativeCleanup called.");

    Java_com_packet_analyzer_data_datasource_native_JniBridge_nativeStopSocketListener(env, thiz);


    if (g_jniBridgeClassRef != NULL) {
        (*env)->DeleteGlobalRef(env, g_jniBridgeClassRef);
        g_jniBridgeClassRef = NULL;
        log_i("nativeCleanup: Global JNI Class reference released.");
    }
    g_onPacketCallbackMethodID = NULL;

    log_i("nativeCleanup finished.");
}