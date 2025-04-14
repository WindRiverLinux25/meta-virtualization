SUMMARY = "User-mode networking for unprivileged network namespaces."
DESCRIPTION = "slirp4netns allows connecting a network namespace to the \
Internet in a completely unprivileged way, by connecting a TAP device in a \
network namespace to the usermode TCP/IP stack ("slirp")."

LICENSE = "GPL-2.0-or-later"
LIC_FILES_CHKSUM = "file://COPYING;md5=1e2efd29c201480c6be2744d9edade26"

SRCREV = "0f13345bcef588d2bb70d662d41e92ee8a816d85"
SRC_URI = "git://github.com/rootless-containers/slirp4netns.git;nobranch=1;protocol=https"

PV = "1.3.2"

inherit features_check
REQUIRED_DISTRO_FEATURES ?= "seccomp"

DEPENDS = "glib-2.0 libcap libseccomp libslirp"

S = "${WORKDIR}/git"

RRECOMMENDS:${PN} += "kernel-module-tun"

inherit autotools pkgconfig
