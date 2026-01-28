#include "network.hpp"

#include <cstdio>
#include <cstring>
#include <unistd.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <netdb.h>
#include <errno.h>

namespace quarto {

Connection::~Connection() {
    close();
}

bool Connection::connect(const char* host, int port) {
    // Resolve hostname
    struct addrinfo hints{}, *res;
    hints.ai_family = AF_INET;
    hints.ai_socktype = SOCK_STREAM;

    char port_str[16];
    snprintf(port_str, sizeof(port_str), "%d", port);

    if (getaddrinfo(host, port_str, &hints, &res) != 0) {
        fprintf(stderr, "Failed to resolve host: %s\n", host);
        return false;
    }

    sockfd_ = socket(res->ai_family, res->ai_socktype, res->ai_protocol);
    if (sockfd_ < 0) {
        fprintf(stderr, "Failed to create socket: %s\n", strerror(errno));
        freeaddrinfo(res);
        return false;
    }

    if (::connect(sockfd_, res->ai_addr, res->ai_addrlen) < 0) {
        fprintf(stderr, "Failed to connect: %s\n", strerror(errno));
        ::close(sockfd_);
        sockfd_ = -1;
        freeaddrinfo(res);
        return false;
    }

    freeaddrinfo(res);
    recv_len_ = 0;
    return true;
}

void Connection::close() {
    running_ = false;
    if (sockfd_ >= 0) {
        ::shutdown(sockfd_, SHUT_RDWR);
        ::close(sockfd_);
        sockfd_ = -1;
    }
    if (receiver_thread_.joinable()) {
        receiver_thread_.join();
    }
}

void Connection::start_receiver() {
    running_ = true;
    receiver_thread_ = std::thread(&Connection::receiver_loop, this);
}

void Connection::receiver_loop() {
    while (running_ && sockfd_ >= 0) {
        ssize_t n = recv(sockfd_, recv_buf_ + recv_len_,
                         sizeof(recv_buf_) - recv_len_ - 1, 0);
        if (n <= 0) {
            if (n < 0 && errno == EINTR) continue;
            break;  // Disconnected or error
        }

        recv_len_ += n;
        recv_buf_[recv_len_] = '\0';

        // Process complete lines
        char* start = recv_buf_;
        char* newline;
        while ((newline = strchr(start, '\n')) != nullptr) {
            *newline = '\0';
            if (on_message_) {
                on_message_(std::string_view(start, newline - start));
            }
            start = newline + 1;
        }

        // Move remaining partial message to front
        size_t remaining = recv_len_ - (start - recv_buf_);
        if (remaining > 0 && start != recv_buf_) {
            memmove(recv_buf_, start, remaining);
        }
        recv_len_ = remaining;
    }
    running_ = false;
}

bool Connection::send(std::string_view msg) {
    if (sockfd_ < 0) return false;

    std::string data(msg);
    data += '\n';

    size_t sent = 0;
    while (sent < data.size()) {
        ssize_t n = ::send(sockfd_, data.data() + sent, data.size() - sent, 0);
        if (n <= 0) {
            if (n < 0 && errno == EINTR) continue;
            return false;
        }
        sent += n;
    }
    return true;
}

int parse_message(std::string_view msg, std::string_view parts[], int max_parts) {
    int count = 0;
    size_t start = 0;

    for (size_t i = 0; i <= msg.size() && count < max_parts; i++) {
        if (i == msg.size() || msg[i] == '~') {
            parts[count++] = msg.substr(start, i - start);
            start = i + 1;
        }
    }
    return count;
}

} // namespace quarto
