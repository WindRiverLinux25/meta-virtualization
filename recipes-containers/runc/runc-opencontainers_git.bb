include runc.inc

SRCREV = "e89a29929c775025419ab0d218a43588b4c12b9a"
SRC_URI = " \
    git://github.com/opencontainers/runc;branch=release-1.2;protocol=https;destsuffix=${GO_SRCURI_DESTSUFFIX} \
    file://0001-Makefile-respect-GOBUILDFLAGS-for-runc-and-remove-re.patch \
    "
RUNC_VERSION = "1.2.6"

# for compatibility with existing RDEPENDS that have existed since
# runc-docker and runc-opencontainers were separate
RPROVIDES:${PN} += "runc-docker"

CVE_PRODUCT = "runc"

LDFLAGS += "${@bb.utils.contains('DISTRO_FEATURES', 'ld-is-gold', ' -fuse-ld=bfd', '', d)}"
