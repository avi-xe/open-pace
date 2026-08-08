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

/**
 * Summary statistics computed from track points.
 */
public class TrackSummary {

    public double totalDistance;      // meters
    public long totalDuration;       // seconds
    public double averagePace;       // seconds per km
    public double elevationGain;     // meters
    public double elevationLoss;     // meters
    public double maxSpeed;          // m/s
    public double averageSpeed;      // m/s

    public TrackSummary() {
    }
}
