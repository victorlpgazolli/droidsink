#include <libusb.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>

#define AMAZON_VID 0x1949
#define ECHO_PID   0x2048
#define GOOGLE_VID 0x18D1
#define BUFFER_SIZE 3840

void send_string(libusb_device_handle *h, int idx, const char *s) {
    libusb_control_transfer(h, LIBUSB_ENDPOINT_OUT | LIBUSB_REQUEST_TYPE_VENDOR, 52, 0, idx, (unsigned char *)s, strlen(s) + 1, 0);
}

int main() {
    libusb_context *ctx;
    libusb_device_handle *h = NULL;
    libusb_init(&ctx);

    h = libusb_open_device_with_vid_pid(ctx, AMAZON_VID, ECHO_PID);
    if (h) {
        printf("Handshake AOA...\n");
        unsigned char proto[2];
        libusb_control_transfer(h, LIBUSB_ENDPOINT_IN | LIBUSB_REQUEST_TYPE_VENDOR, 51, 0, 0, proto, 2, 0);
        send_string(h, 0, "Victor"); send_string(h, 1, "DroidSink");
        send_string(h, 2, "PCM Audio"); send_string(h, 3, "1.0");
        send_string(h, 4, "http://localhost"); send_string(h, 5, "0001");
        libusb_control_transfer(h, LIBUSB_ENDPOINT_OUT | LIBUSB_REQUEST_TYPE_VENDOR, 53, 0, 0, NULL, 0, 0);
        libusb_close(h);
        sleep(2);
    }

    h = NULL;
    while (!h) {
        h = libusb_open_device_with_vid_pid(ctx, GOOGLE_VID, 0x2D01);
        if (!h) h = libusb_open_device_with_vid_pid(ctx, GOOGLE_VID, 0x2D00);
        if (!h) { printf("Aguardando Acessório...\n"); sleep(1); }
    }

    libusb_claim_interface(h, 0);
    printf("Streaming ativo (Pacotes de 2KB)...\n");

    unsigned char buffer[BUFFER_SIZE];
    int transferred;

    while (1) {
        size_t total = 0;
        while (total < BUFFER_SIZE) {
            size_t r = fread(buffer + total, 1, BUFFER_SIZE - total, stdin);
            if (r <= 0) break;
            total += r;
        }

        if (total != BUFFER_SIZE) continue;

        int r = libusb_bulk_transfer(
                h,
                0x01,
                buffer,
                BUFFER_SIZE,
                &transferred,
                0
        );

        if (r != 0) {
            fprintf(stderr, "USB error: %s\n", libusb_error_name(r));
        }
    }

    libusb_release_interface(h, 0);
    libusb_close(h);
    libusb_exit(ctx);
    return 0;
}