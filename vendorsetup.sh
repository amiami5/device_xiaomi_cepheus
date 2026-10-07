#
# Fetch the prebuilt kernel clang used by BoardConfig.mk (clang-r536225)
# from AOSP and link it into prebuilts/clang/host/linux-x86.
#
# Override the download location with CEPHEUS_TOOLCHAIN_DIR (default: ~/toolchains).
#

cepheus_setup_clang() {
    local ver="r536225"
    local branch="android15-qpr1-release"
    local url="https://android.googlesource.com/platform/prebuilts/clang/host/linux-x86/+archive/refs/heads/${branch}/clang-${ver}.tar.gz"
    local top
    top="$(gettop)"
    local cache_dir="${CEPHEUS_TOOLCHAIN_DIR:-$HOME/toolchains}"
    local dest="${cache_dir}/clang-${ver}"
    local link="${top}/prebuilts/clang/host/linux-x86/clang-${ver}"

    if [ ! -x "${dest}/bin/clang" ]; then
        echo "cepheus: downloading clang-${ver} to ${dest}"
        mkdir -p "${dest}" || return 1
        if ! curl -fL --retry 3 "${url}" | tar -xz -C "${dest}"; then
            echo "cepheus: failed to download clang-${ver}" >&2
            rm -rf "${dest}"
            return 1
        fi
    fi

    if [ ! -e "${link}" ]; then
        ln -s "${dest}" "${link}" || return 1
        echo "cepheus: linked ${link} -> ${dest}"
    fi
}

cepheus_setup_clang
unset -f cepheus_setup_clang
