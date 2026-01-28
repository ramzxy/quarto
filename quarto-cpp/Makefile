CXX = g++
CXXFLAGS = -std=c++17 -O3 -march=native -flto -DNDEBUG -Wall -Wextra
LDFLAGS = -pthread

SRCS = src/main.cpp src/network.cpp src/choker_joker.cpp src/search.cpp
OBJS = $(SRCS:.cpp=.o)
TARGET = quarto-ai

all: $(TARGET)

$(TARGET): $(OBJS)
	$(CXX) $(CXXFLAGS) $(LDFLAGS) -o $@ $^

%.o: %.cpp
	$(CXX) $(CXXFLAGS) -c -o $@ $<

clean:
	rm -f $(OBJS) $(TARGET)

.PHONY: all clean
