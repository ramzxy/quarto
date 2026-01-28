#pragma once

#include <cstdint>
#include <string>
#include <string_view>
#include <functional>
#include <atomic>
#include <thread>

namespace quarto {

class Connection {
public:
    using MessageHandler = std::function<void(std::string_view)>;

    Connection() = default;
    ~Connection();

    // Non-copyable
    Connection(const Connection&) = delete;
    Connection& operator=(const Connection&) = delete;

    bool connect(const char* host, int port);
    void close();

    void set_message_handler(MessageHandler handler) { on_message_ = handler; }
    void start_receiver();

    bool send(std::string_view msg);

    bool is_connected() const { return sockfd_ >= 0; }

private:
    void receiver_loop();

    int sockfd_ = -1;
    char recv_buf_[4096];
    size_t recv_len_ = 0;

    std::thread receiver_thread_;
    std::atomic<bool> running_{false};

    MessageHandler on_message_;
};

// Parse message into parts split by '~'
// Returns number of parts
int parse_message(std::string_view msg, std::string_view parts[], int max_parts);

} // namespace quarto
