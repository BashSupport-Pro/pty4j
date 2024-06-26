#*******************************************************************************
# Copyright (c) 2002, 2009 QNX Software Systems and others.
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v1.0
# which accompanies this distribution, and is available at
# http://www.eclipse.org/legal/epl-v10.html
#
# Contributors:
#     QNX Software Systems - initial API and implementation
#     Alex Blewitt - MacOSX with a 64-bit vm 
#*******************************************************************************/

# Defaults which can be overridden.
OS = darwin
LINUX=linux
ARCH_X86 = x86
ARCH_X86_64 = x86_64
ARCH_ARM64 = arm64

CC=gcc
LD=libtool
CPPFLAGS = -I.
CFLAGS +=-fPIC -D_REENTRANT

ARCH_FLAG_X86 = -target i386-apple-macos10.9
ARCH_FLAG_X86_64 = -target x86_64-apple-macos10.9
ARCH_FLAG_ARM64 = -target arm64-apple-macos11

INSTALL_DIR_X86 = ../os/$(OS)/$(ARCH_X86)
INSTALL_DIR_X86_64 = ../os/$(OS)
INSTALL_DIR_LINUX_X86 = ../os/$(LINUX)/$(ARCH_X86)
INSTALL_DIR_LINUX_X86_64 = ../os/$(LINUX)/$(ARCH_X86_64)

LIB_NAME_PTY = libpty-bashpro.so
LIB_NAME_PTY_DARWIN = libpty-bashpro.dylib
LIB_NAME_FULL_PTY_X86 = $(INSTALL_DIR_X86)/libpty-bashpro.dylib
LIB_NAME_FULL_PTY_X86_64 = $(INSTALL_DIR_X86_64)/libpty-bashpro.dylib

LIB_NAME_FULL_PTY_LINUX_X86 = $(INSTALL_DIR_LINUX_X86)/libpty-bashpro.so
LIB_NAME_FULL_PTY_LINUX_X86_64 = $(INSTALL_DIR_LINUX_X86_64)/libpty-bashpro.so

OBJS_PTY_X86 = exec_pty_$(ARCH_X86).o openpty_$(ARCH_X86).o pfind_$(ARCH_X86).o
OBJS_PTY_X86_64 = exec_pty_$(ARCH_X86_64).o openpty_$(ARCH_X86_64).o pfind_$(ARCH_X86_64).o
OBJS_PTY_ARM64 = exec_pty_$(ARCH_ARM64).o openpty_$(ARCH_ARM64).o pfind_$(ARCH_ARM64).o

OBJS_X86 = $(OBJS_PTY_X86)
OBJS_X86_64 = $(OBJS_PTY_X86_64)
OBJS_ARM64 = $(OBJS_PTY_ARM64)

all: x86 x86_64

linux: linux_x86 linux_x86_64

x86: $(LIB_NAME_FULL_PTY_X86)

x86_64: $(LIB_NAME_FULL_PTY_X86_64)

linux_x86: $(LIB_NAME_FULL_PTY_LINUX_X86)
linux_x86_64: $(LIB_NAME_FULL_PTY_LINUX_X86_64)

rebuild: clean all

$(LIB_NAME_FULL_PTY_X86): $(OBJS_PTY_X86)
	mkdir -p $(INSTALL_DIR_X86)
	$(CC) -dynamiclib  $(ARCH_FLAG_X86) -o $(LIB_NAME_FULL_PTY_X86) $(OBJS_PTY_X86) -lc

$(LIB_NAME_FULL_PTY_X86_64): $(OBJS_PTY_X86_64) $(OBJS_PTY_ARM64)
	mkdir -p $(INSTALL_DIR_X86_64) $(ARCH_X86_64) $(ARCH_ARM64)
	$(CC) -dynamiclib $(ARCH_FLAG_X86_64) -o $(ARCH_X86_64)/$(LIB_NAME_PTY_DARWIN) $(OBJS_PTY_X86_64) -lc
	$(CC) -dynamiclib $(ARCH_FLAG_ARM64) -o $(ARCH_ARM64)/$(LIB_NAME_PTY_DARWIN) $(OBJS_PTY_ARM64) -lc
	lipo -create -output $(LIB_NAME_FULL_PTY_X86_64) $(ARCH_X86_64)/$(LIB_NAME_PTY_DARWIN) $(ARCH_ARM64)/$(LIB_NAME_PTY_DARWIN)

$(LIB_NAME_FULL_PTY_LINUX_X86): $(OBJS_PTY_X86)
	mkdir -p $(INSTALL_DIR_LINUX_X86)
	$(CC) -m32 -g -shared -Wl,-soname,$(LIB_NAME_PTY) $(LDFLAGS) -o $(LIB_NAME_FULL_PTY_LINUX_X86) $(OBJS_PTY_X86)

$(LIB_NAME_FULL_PTY_LINUX_X86_64): $(OBJS_PTY_X86_64)
	mkdir -p $(INSTALL_DIR_LINUX_X86_64)
	$(CC) -g -shared -Wl,-soname,$(LIB_NAME_PTY) $(LDFLAGS) -o $(LIB_NAME_FULL_PTY_LINUX_X86_64) $(OBJS_PTY_X86_64)

exec_pty_$(ARCH_X86).o: exec_pty.c
	$(CC) $(CFLAGS) $(ARCH_FLAG_X86) $(CPPFLAGS) -c -o $@ exec_pty.c

openpty_$(ARCH_X86).o: openpty.c
	$(CC) $(CFLAGS) $(ARCH_FLAG_X86) $(CPPFLAGS) -c -o $@ openpty.c

pfind_$(ARCH_X86).o: pfind.c
	$(CC) $(CFLAGS) $(ARCH_FLAG_X86) $(CPPFLAGS) -c -o $@ pfind.c

exec_pty_$(ARCH_X86_64).o: exec_pty.c
	$(CC) $(CFLAGS) $(ARCH_FLAG_X86_64) $(CPPFLAGS) -c -o $@ exec_pty.c

openpty_$(ARCH_X86_64).o: openpty.c
	$(CC) $(CFLAGS) $(ARCH_FLAG_X86_64) $(CPPFLAGS) -c -o $@ openpty.c

pfind_$(ARCH_X86_64).o: pfind.c
	$(CC) $(CFLAGS) $(ARCH_FLAG_X86_64) $(CPPFLAGS) -c -o $@ pfind.c

exec_pty_$(ARCH_ARM64).o: exec_pty.c
	$(CC) $(CFLAGS) $(ARCH_FLAG_ARM64) $(CPPFLAGS) -c -o $@ exec_pty.c

openpty_$(ARCH_ARM64).o: openpty.c
	$(CC) $(CFLAGS) $(ARCH_FLAG_ARM64) $(CPPFLAGS) -c -o $@ openpty.c

pfind_$(ARCH_ARM64).o: pfind.c
	$(CC) $(CFLAGS) $(ARCH_FLAG_ARM64) $(CPPFLAGS) -c -o $@ pfind.c

clean :
	$(RM) $(OBJS_X86) $(LIB_NAME_FULL_PTY_X86)
	$(RM) $(OBJS_X86_64) $(OBJS_ARM64) $(LIB_NAME_PTY_X86_64) $(ARCH_X86_64)/$(LIB_NAME_PTY_DARWIN) $(ARCH_ARM64)/$(LIB_NAME_PTY_DARWIN)
