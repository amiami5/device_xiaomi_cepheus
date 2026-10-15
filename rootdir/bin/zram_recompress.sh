#! /vendor/bin/sh
#
# Copyright (C) 2026 amisuke
#
# SPDX-License-Identifier: Apache-2.0
#
# Periodically recompress cold zram pages with the secondary algorithm.
# Pages that stayed idle for a whole interval are recompressed, then
# everything is marked idle again for the next round.

ZRAM=/sys/block/zram0
INTERVAL=1800

while true; do
    sleep $INTERVAL
    [ -w $ZRAM/recompress ] || continue
    echo "type=idle" > $ZRAM/recompress
    echo all > $ZRAM/idle
done
