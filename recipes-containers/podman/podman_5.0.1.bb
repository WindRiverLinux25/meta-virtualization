HOMEPAGE = "https://podman.io/"
SUMMARY =  "A daemonless container engine"
DESCRIPTION = "Podman is a daemonless container engine for developing, \
    managing, and running OCI Containers on your Linux System. Containers can \
    either be run as root or in rootless mode. Simply put: \
    `alias docker=podman`. \
    "

inherit features_check
REQUIRED_DISTRO_FEATURES ?= "seccomp"

COMPATIBLE_HOST = "^(?!mips).*"

DEPENDS = " \
    gpgme \
    libseccomp \
    ${@bb.utils.filter('DISTRO_FEATURES', 'systemd', d)} \
    gettext-native \
"

SRCREV = "946d055df324e4ed6c1e806b561af4740db4fea9"
SRC_URI = " \
    git://github.com/containers/podman.git;branch=v5.0;protocol=https \
    file://0001-fix-sigstore-verify-failed.patch \
"

LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://src/import/LICENSE;md5=3d9b931fa23ab1cacd0087f9e2ee12c0"

GO_IMPORT = "import"

S = "${WORKDIR}/git"

PACKAGES =+ "${PN}-contrib"

PODMAN_PKG = "github.com/containers/libpod"
BUILDTAGS ?= "seccomp varlink \
${@bb.utils.contains('DISTRO_FEATURES', 'systemd', 'systemd', '', d)} \
${@bb.utils.contains('PACKAGECONFIG', 'cni', 'cni', 'netavark', d)} \
exclude_graphdriver_btrfs exclude_graphdriver_devicemapper"

# overide LDFLAGS to allow podman to build without: "flag provided but not # defined: -Wl,-O1
export LDFLAGS=""
export BUILDFLAGS="${GOBUILDFLAGS}"

inherit go goarch
inherit systemd pkgconfig

do_configure[noexec] = "1"

EXTRA_OEMAKE = " \
     PREFIX=${prefix} BINDIR=${bindir} LIBEXECDIR=${libexecdir} \
     ETCDIR=${sysconfdir} TMPFILESDIR=${nonarch_libdir}/tmpfiles.d \
     SYSTEMDDIR=${systemd_unitdir}/system USERSYSTEMDDIR=${systemd_unitdir}/user \
"

# remove 'docker' from the packageconfig if you don't want podman to
# build and install the docker wrapper. If docker is enabled in the
# packageconfig, the podman package will rconfict with docker.
PACKAGECONFIG ?= "docker"

do_compile() {
	cd ${S}/src
	rm -rf .gopath
	mkdir -p .gopath/src/"$(dirname "${PODMAN_PKG}")"
	ln -sf ../../../../import/ .gopath/src/"${PODMAN_PKG}"

	ln -sf "../../../import/vendor/github.com/varlink/" ".gopath/src/github.com/varlink"

	export GOARCH="${BUILD_GOARCH}"
	export GOPATH="${S}/src/.gopath"
	export GOROOT="${STAGING_DIR_NATIVE}/${nonarch_libdir}/${HOST_SYS}/go"

	cd ${S}/src/.gopath/src/"${PODMAN_PKG}"

	# Pass the needed cflags/ldflags so that cgo
	# can find the needed headers files and libraries
	export GOARCH=${TARGET_GOARCH}
	export CGO_ENABLED="1"
	export CGO_CFLAGS="${CFLAGS} --sysroot=${STAGING_DIR_TARGET}"
	export CGO_LDFLAGS="${LDFLAGS} --sysroot=${STAGING_DIR_TARGET}"

	# podman now builds go-md2man and requires the host/build details
	export NATIVE_GOOS=${BUILD_GOOS}
	export NATIVE_GOARCH=${BUILD_GOARCH}

	oe_runmake NATIVE_GOOS=${BUILD_GOOS} NATIVE_GOARCH=${BUILD_GOARCH} BUILDTAGS="${BUILDTAGS}"
}

do_install() {
	cd ${S}/src/.gopath/src/"${PODMAN_PKG}"

	export GOARCH="${BUILD_GOARCH}"
	export GOPATH="${S}/src/.gopath"
	export GOROOT="${STAGING_DIR_NATIVE}/${nonarch_libdir}/${HOST_SYS}/go"

	oe_runmake install DESTDIR="${D}"
	if ${@bb.utils.contains('PACKAGECONFIG', 'docker', 'true', 'false', d)}; then
		oe_runmake install.docker DESTDIR="${D}"
	fi

	# Silence docker emulation warnings.
	mkdir -p ${D}${sysconfdir}/containers
	touch ${D}${sysconfdir}/containers/nodocker
}

FILES:${PN} += " \
    ${systemd_unitdir}/system/* \
    ${nonarch_libdir}/systemd/* \
    ${systemd_unitdir}/user/* \
    ${nonarch_libdir}/tmpfiles.d/* \
    ${sysconfdir}/cni \
    ${datadir}/user-tmpfiles.d/* \
"

# Fix ELF binary /usr/bin/podman has relocations in .text
#lib32-podman: ELF binary /usr/bin/podman-remote has relocations in .text [textrel]
INSANE_SKIP:${PN} += "textrel"

SYSTEMD_SERVICE:${PN} = "podman.service podman.socket"

RDEPENDS:${PN} += "conmon virtual-runc iptables ${@bb.utils.contains('PACKAGECONFIG', 'cni', 'cni', 'netavark', d)} skopeo fuse-overlayfs util-linux-nsenter"
RRECOMMENDS:${PN} += "slirp4netns \
                      kernel-module-xt-masquerade \
                      kernel-module-xt-comment \
                      kernel-module-xt-addrtype \
                      kernel-module-xt-conntrack \
                      kernel-module-xt-mark \
                      kernel-module-xt-multiport \
                      kernel-module-xt-nat \
                      kernel-module-xt-tcpudp"
RCONFLICTS:${PN} = "${@bb.utils.contains('PACKAGECONFIG', 'docker', 'docker', '', d)}"
