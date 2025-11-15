//
// Created by Fredric Newberg on 6/2/23.
//

#include <fcntl.h>
#include <unistd.h>
#include "file_marker.h"

/*
 * Write a file to the crash marker location to indicate that a crash has occurred. This is the
 * same path that we write to from the JVM to indicate that a JVM crash has occurred.
 *
 * This function is safe to call from a signal handler.
 */
void emb_write_crash_marker_file(emb_env *env, const char *source) {
    // Open the file for writing, truncating it if it already exists.
    int fd = open(env->crash_marker_path, O_CREAT | O_WRONLY | O_TRUNC, 0644);
    if (fd > 0) {
        write(fd, source, 1);
        close(fd);
    }
}
