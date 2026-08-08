/*
 * Copyright 2024 Open Pace Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openpace.activity;

import java.time.Instant;

/**
 * Represents a single point in a GPX track.
 */
public class TrackPoint {

    public double latitude;
    public double longitude;
    public double elevation;    // meters
    public Instant timestamp;
    public double speed;        // m/s (calculated from timestamps)

    public TrackPoint() {
    }

    public TrackPoint(double latitude, double longitude, double elevation,
                      Instant timestamp, double speed) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.elevation = elevation;
        this.timestamp = timestamp;
        this.speed = speed;
    }
}
