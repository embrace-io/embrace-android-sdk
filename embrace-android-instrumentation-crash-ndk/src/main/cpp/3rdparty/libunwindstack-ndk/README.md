# libunwindstack-ndk

This repository contains a patched version of libunwindstack to build for NDK.

We removed some parts of the code that we won't use, like MIPS, bionic and windows support, but there's still room for improvements and unused code removal.

We also had to add headers from libraries like art_api, android-base and procinfo.

We got the libunwindstack library from here: https://android.googlesource.com/platform/system/core/+/refs/heads/master

The commit was c1f66b44fbf8c115f008d625f409449f111cdfa0: 
https://android.googlesource.com/platform/system/unwinding/+/c1f66b44fbf8c115f008d625f409449f111cdfa0

If we wanted to update again, we could grab the latest commit, overwrite the whole library, and remove the things we don't use.
