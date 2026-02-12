#!/bin/sh

set -u

log() {
    echo "[rk3399-rt-tune] $*" > /dev/console 2>/dev/null || true
}

set_governor() {
    gov="$1"
    for p in /sys/devices/system/cpu/cpufreq/policy*; do
        [ -d "$p" ] || continue
        if [ -w "$p/scaling_governor" ]; then
            echo "$gov" > "$p/scaling_governor" 2>/dev/null || true
        fi
    done
}

set_min_freq_to_max() {
    for p in /sys/devices/system/cpu/cpufreq/policy*; do
        [ -d "$p" ] || continue
        maxf=""
        [ -r "$p/cpuinfo_max_freq" ] && maxf=$(cat "$p/cpuinfo_max_freq" 2>/dev/null || true)
        if [ -n "$maxf" ] && [ -w "$p/scaling_min_freq" ]; then
            echo "$maxf" > "$p/scaling_min_freq" 2>/dev/null || true
        fi
    done
}

disable_cpuidle_deep_states() {
    for cpu in /sys/devices/system/cpu/cpu[0-9]*; do
        [ -d "$cpu" ] || continue
        if [ -d "$cpu/cpuidle" ]; then
            for st in "$cpu"/cpuidle/state*; do
                [ -d "$st" ] || continue
                name=""
                [ -r "$st/name" ] && name=$(cat "$st/name" 2>/dev/null || true)
                case "$name" in
                    C1|WFI)
                        ;;
                    *)
                        [ -w "$st/disable" ] && echo 1 > "$st/disable" 2>/dev/null || true
                        ;;
                esac
            done
        fi
    done
}

boost_rt_runtime() {
    if [ -w /proc/sys/kernel/sched_rt_runtime_us ]; then
        echo -1 > /proc/sys/kernel/sched_rt_runtime_us 2>/dev/null || true
    fi
}

main() {
    log "Applying RT tuning: governor=performance, disable deep idle, rt runtime"
    set_governor performance
    set_min_freq_to_max
    disable_cpuidle_deep_states
    boost_rt_runtime
    log "Done"
}

main "$@"
