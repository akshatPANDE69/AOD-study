CC := clang
CFLAGS := -O3 -Wall -Wextra -Iinclude
LDFLAGS := -lm

TARGET := aod_blitter
BENCHMARK := bench_aod_micro

SRC_DIR := src
BENCH_DIR := bench
INCLUDE_DIR := include

SRCS := $(SRC_DIR)/aod_simd_blitter.c $(SRC_DIR)/aod_engine.c
BENCH_SRCS := $(BENCH_DIR)/bench_aod_micro.c

OBJS := $(SRCS:.c=.o)
BENCH_OBJS := $(BENCH_SRCS:.c=.o)

DEVICE_RUNNER := aod_device_runner
DEVICE_RUNNER_SRC := $(BENCH_DIR)/aod_device_runner.c
DEVICE_RUNNER_OBJ := $(DEVICE_RUNNER_SRC:.c=.o)

.PHONY: all clean bench run lib runner

all: lib bench runner

lib: $(TARGET).a

$(TARGET).a: $(OBJS)
	ar rcs $@ $^

bench: $(BENCHMARK)

runner: $(DEVICE_RUNNER)

$(DEVICE_RUNNER): $(DEVICE_RUNNER_OBJ) $(OBJS)
	$(CC) $(CFLAGS) -o $@ $^ $(LDFLAGS)

$(BENCHMARK): $(BENCH_OBJS) $(OBJS)
	$(CC) $(CFLAGS) -o $@ $^ $(LDFLAGS)

$(SRC_DIR)/%.o: $(SRC_DIR)/%.c
	$(CC) $(CFLAGS) -c -o $@ $<

$(BENCH_DIR)/%.o: $(BENCH_DIR)/%.c
	$(CC) $(CFLAGS) -c -o $@ $<

run: bench
	@./$(BENCHMARK)

clean:
	@rm -f $(OBJS) $(BENCH_OBJS) $(DEVICE_RUNNER_OBJ) $(TARGET).a $(BENCHMARK) $(DEVICE_RUNNER)

# Cross-compilation note for Android:
# To cross-compile for Android with NDK clang:
#   export ANDROID_NDK=/path/to/android-ndk
#   cmake -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake \
#         -DANDROID_ABI=arm64-v8a -DCMAKE_C_COMPILER=$ANDROID_NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android21-clang \
#         -DCMAKE_BUILD_TYPE=Release .
#   make lib
# Or manually:
#   clang --target=aarch64-linux-android21 -O3 -Wall -Wextra -Iinclude \
#         src/aod_simd_blitter.c src/aod_engine.c -c -o aod_blitter-android.o

# For ARMv7-A with NEON:
#   clang --target=armv7a-linux-androideabi21 -march=armv7-a -mfloat-abi=hard -mfpu=neon \
#         -O3 -Wall -Wextra -Iinclude src/aod_simd_blitter.c src/aod_engine.c -c -o aod_blitter-android-arm.o