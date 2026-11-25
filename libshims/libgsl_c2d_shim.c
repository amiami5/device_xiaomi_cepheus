#define LOG_TAG "libgsl_c2d_shim"

#include <dlfcn.h>
#include <log/log.h>
#include <pthread.h>
#include <stdint.h>

typedef int (*gsl_memory_alloc_pure_64_fn)(uint64_t sizebytes, uint64_t flags, void* memdesc);

static pthread_once_t g_init_once = PTHREAD_ONCE_INIT;
static gsl_memory_alloc_pure_64_fn g_real_alloc_64 = NULL;

static void init_gsl_symbols(void) {
    void* handle = dlopen("libgsl.so", RTLD_NOW | RTLD_NOLOAD);
    if (!handle) {
        handle = dlopen("libgsl.so", RTLD_NOW);
    }
    if (!handle) {
        ALOGE("Failed to open libgsl.so: %s", dlerror());
        return;
    }
    g_real_alloc_64 = (gsl_memory_alloc_pure_64_fn)dlsym(handle, "gsl_memory_alloc_pure_64");
    if (!g_real_alloc_64) {
        ALOGE("Failed to resolve gsl_memory_alloc_pure_64: %s", dlerror());
    }
}

/*
 * V@0530.57 32-bit libC2D2.so calls gsl_memory_alloc_pure with 32-bit flags:
 *   int gsl_memory_alloc_pure(uint32_t sizebytes, uint32_t flags, void *memdesc)
 *     r0 = sizebytes, r1 = flags, r2 = memdesc
 *
 * V@0615.95 32-bit libgsl.so changed flags to uint64_t:
 *   int gsl_memory_alloc_pure(uint32_t sizebytes, uint64_t flags, void *memdesc)
 *     r0 = sizebytes, r2:r3 = flags, [sp+0] = memdesc
 *
 * Forward the legacy 32-bit call to gsl_memory_alloc_pure_64 with widened arguments.
 */
__attribute__((visibility("default")))
int gsl_memory_alloc_pure(uint32_t sizebytes, uint32_t flags, void* memdesc) {
    pthread_once(&g_init_once, init_gsl_symbols);
    if (!g_real_alloc_64) {
        return -1;
    }
    return g_real_alloc_64((uint64_t)sizebytes, (uint64_t)flags, memdesc);
}

/*
 * V@0530.57 32-bit libc2d30_bltlib.so imports __aeabi_ul2d from libgsl.so,
 * which is no longer exported by V@0615.95 libgsl.so.
 * Compute via 32-bit halves to avoid self-recursion.
 */
__attribute__((visibility("default")))
double __aeabi_ul2d(uint64_t val) {
    uint32_t hi = (uint32_t)(val >> 32);
    uint32_t lo = (uint32_t)(val & 0xffffffffU);
    return (double)hi * 4294967296.0 + (double)lo;
}
