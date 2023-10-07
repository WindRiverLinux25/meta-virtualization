HOMEPAGE = "http://www.docker.com"
SUMMARY = "Linux container runtime"
DESCRIPTION = "Linux container runtime \
 Docker complements kernel namespacing with a high-level API which \
 operates at the process level. It runs unix processes with strong \
 guarantees of isolation and repeatability across servers. \
 . \
 Docker is a great building block for automating distributed systems: \
 large-scale web deployments, database clusters, continuous deployment \
 systems, private PaaS, service-oriented architectures, etc. \
 . \
 This package contains the daemon and client, which are \
 officially supported on x86_64 and arm hosts. \
 Other architectures are considered experimental. \
 . \
 Also, note that kernel version 3.10 or above is required for proper \
 operation of the daemon process, and that any lower versions may have \
 subtle and/or glaring issues. \
 "

# Notes:
#   - This docker variant uses moby and the other individually maintained
#     upstream variants for SRCREVs
#   - It is a true community / upstream tracking build, and is not a
#     docker curated set of commits or additions
#   - The version number on this package tracks the versions assigned to
#     the curated docker-ce repository. This allows compatibility and
#     functional equivalence, while allowing new features to be more
#     easily added.
#   - The common components of this recipe and docker-ce do need to be moved
#     to a docker.inc recipe
#
# Packaging details:
#
# https://github.com/docker/docker-ce-packaging.git
#  common.mk:
#    DOCKER_CLI_REPO    ?= https://github.com/docker/cli.git
#    DOCKER_ENGINE_REPO ?= https://github.com/docker/docker.git
#    REF                ?= HEAD
#    DOCKER_CLI_REF     ?= $(REF)
#    DOCKER_ENGINE_REF  ?= $(REF)
#
# These follow the tags for our releases in the listed repositories
# so we get that tag, and make it our SRCREVS:
#

SRCREV_moby = "1a7969545d73537545645f5cd2c79b7a77e7d39f"
SRCREV_libnetwork = "67e0588f1ddfaf2faf4c8cae8b7ea2876434d91c"
SRCREV_cli = "ed223bc820ee9bb7005a333013b86203a9e1bc23"
SRCREV_FORMAT = "moby_libnetwork"
SRC_URI = "\
	git://github.com/moby/moby.git;branch=24.0;name=moby;protocol=https \
	git://github.com/docker/libnetwork.git;branch=master;name=libnetwork;destsuffix=git/libnetwork;protocol=https \
	git://github.com/docker/cli;branch=24.0;name=cli;destsuffix=git/cli;protocol=https \
	file://docker.init \
	file://0001-libnetwork-use-GO-instead-of-go.patch \
        file://0001-cli-use-external-GO111MODULE-and-cross-compiler.patch \
        file://0001-dynbinary-use-go-cross-compiler.patch;patchdir=src/import \
	"

DOCKER_COMMIT = "${SRCREV_moby}"

DEPENDS = " \
    go-cli \
    go-pty \
    go-context \
    go-mux \
    go-patricia \
    go-logrus \
    go-fsnotify \
    go-dbus \
    go-capability \
    go-systemd \
    btrfs-tools \
    sqlite3 \
    go-distribution \
    compose-file \
    go-connections \
    notary \
    grpc-go \
    libtool-native \
    libtool \
    "

DEPENDS:append:class-target = " lvm2"
RDEPENDS:${PN} = "util-linux util-linux-unshare iptables \
                  ${@bb.utils.contains('DISTRO_FEATURES', 'aufs', 'aufs-util', '', d)} \
                  ${@bb.utils.contains('DISTRO_FEATURES', 'systemd', '', 'cgroup-lite', d)} \
                  bridge-utils \
                  ca-certificates \
                 "
RDEPENDS:${PN} += "virtual-containerd ${VIRTUAL-RUNTIME_container_runtime}"

RRECOMMENDS:${PN} = "kernel-module-dm-thin-pool kernel-module-nf-nat kernel-module-nf-conntrack-netlink kernel-module-xt-addrtype kernel-module-xt-masquerade"

PROVIDES += "virtual/docker docker"

# we want all the docker variant recpes to be installable via "docker"
PACKAGE_NAME = "docker"
RPROVIDES:${PN} += "docker"
RPROVIDES:${PN}-dbg += "docker-dbg"
RPROVIDES:${PN}-dev += "docker-dev"
RPROVIDES:${PN}-contrip += "docker-dev"

inherit pkgconfig
PACKAGECONFIG ??= "docker-init seccomp"
PACKAGECONFIG[seccomp] = "seccomp,,libseccomp"
PACKAGECONFIG[docker-init] = ",,,docker-init"
PACKAGECONFIG[transient-config] = "transient-config"


GO_IMPORT = "import"
S = "${WORKDIR}/git"


inherit systemd update-rc.d
inherit go
inherit goarch
inherit pkgconfig

do_configure[noexec] = "1"

# Export for possible use in Makefiles, default value comes from go.bbclass
export GO_LINKSHARED

DOCKER_PKG="github.com/docker/docker"
# in order to exclude devicemapper and btrfs - https://github.com/docker/docker/issues/14056
BUILD_TAGS ?= "exclude_graphdriver_btrfs exclude_graphdriver_devicemapper"

do_compile() {
	# Set GOPATH. See 'PACKAGERS.md'. Don't rely on
	# docker to download its dependencies but rather
	# use dependencies packaged independently.
	cd ${S}/src/import
	rm -rf .gopath
	mkdir -p .gopath/src/"$(dirname "${DOCKER_PKG}")"
	ln -sf ../../../.. .gopath/src/"${DOCKER_PKG}"
	
	mkdir -p .gopath/src/github.com/docker
	ln -sf ${WORKDIR}/git/libnetwork .gopath/src/github.com/docker/libnetwork
	ln -sf ${WORKDIR}/git/cli .gopath/src/github.com/docker/cli

	export GOPATH="${S}/src/import/.gopath:${S}/src/import/vendor:${STAGING_DIR_TARGET}/${prefix}/local/go"
	export GOROOT="${STAGING_DIR_NATIVE}/${nonarch_libdir}/${HOST_SYS}/go"

	# Pass the needed cflags/ldflags so that cgo
	# can find the needed headers files and libraries
	export GOARCH=${TARGET_GOARCH}
	export CGO_ENABLED="1"
	export CGO_CFLAGS="${CFLAGS} --sysroot=${STAGING_DIR_TARGET}"
	export CGO_LDFLAGS="${LDFLAGS} --sysroot=${STAGING_DIR_TARGET}"
	export DOCKER_BUILDTAGS='${BUILD_TAGS} ${PACKAGECONFIG_CONFARGS}'
	export GO111MODULE=off

	export DISABLE_WARN_OUTSIDE_CONTAINER=1

	cd ${S}/src/import/

	# this is the unsupported built structure
	# that doesn't rely on an existing docker
	# to build this:
	VERSION="${DOCKER_VERSION}" DOCKER_GITCOMMIT="${DOCKER_COMMIT}" ./hack/make.sh dynbinary

        # build the cli
	cd ${S}/src/import/.gopath/src/github.com/docker/cli
	export CFLAGS=""
	export LDFLAGS=""
	export DOCKER_VERSION=${DOCKER_VERSION}
	VERSION="${DOCKER_VERSION}" DOCKER_GITCOMMIT="${DOCKER_COMMIT}" make dynbinary

	# build the proxy
	cd ${S}/src/import/.gopath/src/github.com/docker/libnetwork
	oe_runmake cross-local
}

do_install() {
	mkdir -p ${D}/${bindir}
	cp ${WORKDIR}/git/cli/build/docker ${D}/${bindir}/docker
	cp ${S}/src/import/bundles/dynbinary-daemon/dockerd ${D}/${bindir}/dockerd
	cp ${WORKDIR}/git/libnetwork/bin/docker-proxy* ${D}/${bindir}/docker-proxy

	if ${@bb.utils.contains('DISTRO_FEATURES','systemd','true','false',d)}; then
		install -d ${D}${systemd_unitdir}/system
		install -m 644 ${S}/src/import/contrib/init/systemd/docker.* ${D}/${systemd_unitdir}/system
		# replaces one copied from above with one that uses the local registry for a mirror
		install -m 644 ${S}/src/import/contrib/init/systemd/docker.service ${D}/${systemd_unitdir}/system
		rm -f ${D}/${systemd_unitdir}/system/docker.service.rpm
	else
		install -d ${D}${sysconfdir}/init.d
		install -m 0755 ${WORKDIR}/docker.init ${D}${sysconfdir}/init.d/docker.init
	fi
	# TLS key that docker creates at run-time if not found is what resides here
	if ${@bb.utils.contains('PACKAGECONFIG','transient-config','true','false',d)}; then
		install -d ${D}${sysconfdir}
		ln -s ..${localstatedir}/run/docker ${D}${sysconfdir}/docker
	else
		install -d ${D}${sysconfdir}/docker
	fi

	mkdir -p ${D}${datadir}/docker/
	install -m 0755 ${S}/src/import/contrib/check-config.sh ${D}${datadir}/docker/
}


SYSTEMD_PACKAGES = "${@bb.utils.contains('DISTRO_FEATURES','systemd','${PN}','',d)}"
SYSTEMD_SERVICE:${PN} = "${@bb.utils.contains('DISTRO_FEATURES','systemd','docker.socket','',d)}"
SYSTEMD_AUTO_ENABLE:${PN} = "enable"

# inverted logic warning. We ony want the sysvinit init to be installed if systemd
# is NOT in the distro features
INITSCRIPT_PACKAGES += "${@bb.utils.contains('DISTRO_FEATURES','systemd','', '${PN}',d)}"
INITSCRIPT_NAME:${PN} = "${@bb.utils.contains('DISTRO_FEATURES','systemd','', 'docker.init',d)}"
INITSCRIPT_PARAMS:${PN} = "defaults"

inherit useradd
USERADD_PACKAGES = "${PN}"
GROUPADD_PARAM:${PN} = "-r docker"

COMPATIBLE_HOST = "^(?!(qemu)?mips).*"

INSANE_SKIP:${PN} += "ldflags textrel"

FILES:${PN} += "${systemd_unitdir}/system/* ${sysconfdir}/docker"

PACKAGES =+ "${PN}-contrib"
FILES:${PN}-contrib += "${datadir}/docker/check-config.sh"
RDEPENDS:${PN}-contrib += "bash"

# By the docker-packaging repository and https://docs.docker.com/engine/install/centos/#installation-methods
# docker is packaged by most distros with a split between the engine and the CLI.
#
# We do the same here, by introducing the -cli package
#
# But to keep existing use cases working, we also create a RDEPENDS between the main
# docker package (the engine) and the cli, so existing "docker" package installs will
# continue to work the same way. To have separate and non-redepending packages created
# set the DOCKER_UNIFIED_PACKAGE variable to False
#
PACKAGES =+ "${PN}-cli"
FILES:${PN}-cli += "${bindir}/docker"

# set to "False" if packages should be generated for the cli and engine, and
# NOT rdepend to get a classic one-package install
DOCKER_UNIFIED_PACKAGE ?= "True"
RDEPENDS:${PN} += "${@bb.utils.contains("DOCKER_UNIFIED_PACKAGE", "True", "${PN}-cli", "", d)}"

# Apache-2.0 for docker
LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://src/import/LICENSE;md5=4859e97a9c7780e77972d989f0823f28"

DOCKER_VERSION = "24.0.6"
PV = "24.0.6"

CVE_PRODUCT = "docker mobyproject:moby"
