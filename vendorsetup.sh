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

# Make sure the kernel's submodules (KernelSU-Next) are initialised and at the
# commit pinned by the kernel tree. drivers/kernelsu is a symlink into it, so
# a missing checkout breaks Kconfig. pipa uses the same KernelSU-Next (legacy),
# so the official KernelSU-Next manager works on both.
cepheus_setup_kernel_submodules() {
    local kdir
    kdir="$(gettop)/kernel/xiaomi/cepheus"
    [ -e "${kdir}/.git" ] || return 0
    [ -f "${kdir}/.gitmodules" ] || return 0

    if ! git -C "${kdir}" submodule update --init KernelSU-Next >/dev/null 2>&1; then
        echo "cepheus: failed to update KernelSU-Next submodule in ${kdir}" >&2
        return 1
    fi
}

# Apply the patches in patches/<path>/ on top of the shared HAL/framework trees
# (hardware/qcom-caf/sm8250/*, frameworks/opt/telephony). Every change is
# guarded by device/config conditions so other devices are unaffected.
# The patches are applied to the working tree only, so a repo sync can reset
# them; they are put back the next time envsetup runs.
cepheus_apply_patches() {
    local top pdir rel repo tmp_index tree f
    top="$(gettop)"
    pdir="${top}/device/xiaomi/cepheus/patches"
    tmp_index="$(mktemp)"

    for rel in \
        hardware/qcom-caf/sm8250/display \
        hardware/qcom-caf/sm8250/audio \
        hardware/qcom-caf/sm8250/media \
        frameworks/opt/telephony; do
        repo="${top}/${rel}"
        [ -e "${repo}/.git" ] || continue
        [ -d "${pdir}/${rel}" ] || continue

        # Tree that HEAD + all patches should produce
        rm -f "${tmp_index}"
        GIT_INDEX_FILE="${tmp_index}" git -C "${repo}" read-tree HEAD || continue
        tree=""
        for f in "${pdir}/${rel}"/*.patch; do
            [ -f "${f}" ] || continue
            if ! GIT_INDEX_FILE="${tmp_index}" git -C "${repo}" apply --cached "${f}"; then
                echo "cepheus: ${f##*/} does not apply to ${repo}" >&2
                continue 2
            fi
        done
        tree="$(GIT_INDEX_FILE="${tmp_index}" git -C "${repo}" write-tree)"

        if git -C "${repo}" diff --quiet "${tree}" --; then
            continue
        fi
        if ! git -C "${repo}" diff --quiet HEAD --; then
            echo "cepheus: ${repo} has local changes, not applying patches" >&2
            continue
        fi
        for f in "${pdir}/${rel}"/*.patch; do
            [ -f "${f}" ] || continue
            git -C "${repo}" apply "${f}" || { rm -f "${tmp_index}"; return 1; }
        done
        echo "cepheus: applied patches to ${rel}"
    done
    rm -f "${tmp_index}"
}

cepheus_setup_clang
cepheus_setup_kernel_submodules
cepheus_apply_patches
unset -f cepheus_setup_clang cepheus_setup_kernel_submodules cepheus_apply_patches
