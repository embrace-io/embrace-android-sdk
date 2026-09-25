# Runs a :embrace-perfetto-analysis gradle task with the arguments it was given.
#
# `root` is resolved from the sourcing script's $0, so every script that sources
# this must sit in scripts/. The tasks own their usage and validation, so nothing is checked here.
#
# Gradle returns its own exit value, so a failing analysis surfaces as a build failure naming the
# exit value the tool asked for rather than as that value.

root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)

run_analysis() {
    task=$1
    shift

    if [ "$#" -eq 0 ]; then
        exec "$root/gradlew" -q --console=plain -p "$root" ":embrace-perfetto-analysis:$task"
    fi

    # quoted one at a time, since gradle splits --args on whitespace
    args=""
    for arg in "$@"; do
        args="$args${args:+ }'$arg'"
    done

    exec "$root/gradlew" -q --console=plain -p "$root" ":embrace-perfetto-analysis:$task" --args="$args"
}
