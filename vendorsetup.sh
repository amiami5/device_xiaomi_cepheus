#
# Build and install llvm-clang (clang + lld) into ../toolchains/llvm-clang
# (sibling of the ROM top directory) if not already present.
#
# Override the base location with CEPHEUS_TOOLCHAIN_DIR (default: <top>/../toolchains).
# Set CEPHEUS_SKIP_LLVM_BUILD=1 to skip automatic building.
#

cepheus_setup_llvm_clang() {
    local top
    top="$(gettop)"
    local cache_dir="${CEPHEUS_TOOLCHAIN_DIR:-$(dirname "${top}")/toolchains}"
    local src_dir="${cache_dir}/llvm-project"
    local build_dir="${src_dir}/build"
    local install_dir="${cache_dir}/llvm-clang"
    local llvm_rev="f42c87ba2c70219b1f4ad1f031a5b6a1ea6603fb"

    if [ -x "${install_dir}/bin/clang" ] && [ -x "${install_dir}/bin/ld.lld" ]; then
        return 0
    fi

    if [ "${CEPHEUS_SKIP_LLVM_BUILD:-0}" = "1" ]; then
        echo "cepheus: skipping llvm-clang build (CEPHEUS_SKIP_LLVM_BUILD=1)"
        return 0
    fi

    for cmd in git cmake ninja python3; do
        if ! command -v "${cmd}" >/dev/null 2>&1; then
            echo "cepheus: missing required tool '${cmd}' to build llvm-clang" >&2
            return 1
        fi
    done

    mkdir -p "${cache_dir}" || return 1

    if [ ! -d "${src_dir}/.git" ]; then
        echo "cepheus: fetching llvm-project (${llvm_rev}) to ${src_dir}"
        mkdir -p "${src_dir}" || return 1
        git -C "${src_dir}" init -q || return 1
        git -C "${src_dir}" remote add origin https://github.com/llvm/llvm-project.git || return 1
        if ! git -C "${src_dir}" fetch --depth 1 origin "${llvm_rev}"; then
            echo "cepheus: failed to fetch llvm-project" >&2
            rm -rf "${src_dir}"
            return 1
        fi
        git -C "${src_dir}" checkout -q FETCH_HEAD || return 1
    fi

    echo "cepheus: configuring llvm-clang in ${build_dir}"
    cmake -S "${src_dir}/llvm" -B "${build_dir}" -G Ninja \
        -DCMAKE_BUILD_TYPE=Release \
        -DCMAKE_INSTALL_PREFIX="${install_dir}" \
        -DLLVM_ENABLE_PROJECTS="clang;lld" \
        -DLLVM_TARGETS_TO_BUILD="AArch64;ARM;X86" \
        -DLLVM_ENABLE_ASSERTIONS=OFF || return 1

    echo "cepheus: building llvm-clang (this may take a while on first run)"
    cmake --build "${build_dir}" -j"$(nproc)" || return 1

    echo "cepheus: installing llvm-clang to ${install_dir}"
    cmake --install "${build_dir}" || return 1
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

cepheus_setup_llvm_clang
cepheus_apply_patches
unset -f cepheus_setup_llvm_clang cepheus_apply_patches
