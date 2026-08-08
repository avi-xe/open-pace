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
package org.openpace.analytics;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.openpace.activity.Activity;

/**
 * Compares an activity's metrics against the user's historical averages.
 */
@ApplicationScoped
@Transactional
public class ComparisonService {

    private static final Logger LOG = Logger.getLogger(ComparisonService.class.getName());

    /**
     * Calculate comparisons for an activity vs the user's historical averages.
     *
     * @param activity the activity to compare
     * @return list of comparison records for pace, distance, duration, elevation
     */
    public List<ActivityComparison> calculateComparisons(Activity activity) {
        List<ActivityComparison> comparisons = new ArrayList<>();

        JsonNode trackData = activity.trackData;
        if (trackData == null || !trackData.has("summary")) {
            LOG.warning("Activity " + activity.id + " has no track data summary");
            return comparisons;
        }

        JsonNode summary = trackData.get("summary");

        Long actorId = activity.actor.id;
        String activityType = activity.activityType;

        UserAverages averages = calculateUserAverages(actorId, activityType);
        if (averages.activityCount == 0) {
            LOG.info("No historical activities found for actor " + actorId
                    + " type " + activityType + "; skipping comparison");
            return comparisons;
        }

        // Extract activity values from summary
        double totalDistance = summary.has("totalDistance")
                ? summary.get("totalDistance").asDouble() : 0.0;
        double totalDuration = summary.has("totalDuration")
                ? summary.get("totalDuration").asDouble() : 0.0;
        double averagePace = summary.has("averagePace")
                ? summary.get("averagePace").asDouble() : 0.0;
        double elevationGain = summary.has("elevationGain")
                ? summary.get("elevationGain").asDouble() : 0.0;

        // Compare each metric
        if (averagePace > 0 && averages.averagePace > 0) {
            comparisons.add(compareMetric(activity, "pace", averagePace, averages.averagePace));
        }
        if (totalDistance > 0 && averages.averageDistance > 0) {
            comparisons.add(compareMetric(activity, "distance", totalDistance, averages.averageDistance));
        }
        if (totalDuration > 0 && averages.averageDuration > 0) {
            comparisons.add(compareMetric(activity, "duration", totalDuration, averages.averageDuration));
        }
        if (elevationGain > 0 && averages.averageElevation > 0) {
            comparisons.add(compareMetric(activity, "elevation", elevationGain, averages.averageElevation));
        }

        // Persist all comparisons
        for (ActivityComparison comparison : comparisons) {
            comparison.persist();
        }

        return comparisons;
    }

    /**
     * Calculate user's average metrics for an activity type.
     *
     * @param actorId the actor id
     * @param activityType the activity type
     * @return averages across historical activities
     */
    UserAverages calculateUserAverages(Long actorId, String activityType) {
        UserAverages averages = new UserAverages();

        List<Activity> activities = Activity.find(
                "activityType = ?1 and actor.id = ?2", activityType, actorId
        ).list();

        if (activities.isEmpty()) {
            return averages;
        }

        double totalDistance = 0;
        double totalDuration = 0;
        double totalPaceWeighted = 0;
        double totalPaceDistance = 0;
        double totalElevation = 0;
        int count = 0;

        for (Activity a : activities) {
            if (a.trackData == null || !a.trackData.has("summary")) {
                continue;
            }

            JsonNode summary = a.trackData.get("summary");

            double distance = summary.has("totalDistance")
                    ? summary.get("totalDistance").asDouble() : 0;
            double duration = summary.has("totalDuration")
                    ? summary.get("totalDuration").asDouble() : 0;
            double pace = summary.has("averagePace")
                    ? summary.get("averagePace").asDouble() : 0;
            double elevation = summary.has("elevationGain")
                    ? summary.get("elevationGain").asDouble() : 0;

            if (distance > 0 && duration > 0) {
                totalDistance += distance;
                totalDuration += duration;
                totalElevation += elevation;

                // Weighted pace: pace * distance, then divide by total distance
                if (pace > 0) {
                    totalPaceWeighted += pace * distance;
                    totalPaceDistance += distance;
                }

                count++;
            }
        }

        if (count == 0) {
            return averages;
        }

        averages.averageDistance = totalDistance / count;
        averages.averageDuration = totalDuration / count;
        averages.averageElevation = totalElevation / count;
        averages.averagePace = totalPaceDistance > 0
                ? totalPaceWeighted / totalPaceDistance : 0;
        averages.activityCount = count;

        return averages;
    }

    /**
     * Compare two values and determine if the activity is an improvement.
     *
     * @param activity the activity being compared
     * @param metricName the metric name
     * @param activityValue the activity's value for this metric
     * @param userAverage the user's average for this metric
     * @return the comparison record
     */
    ActivityComparison compareMetric(Activity activity, String metricName,
            double activityValue, double userAverage) {
        ActivityComparison comparison = new ActivityComparison();
        comparison.activity = activity;
        comparison.metricName = metricName;
        comparison.activityValue = activityValue;
        comparison.userAverage = userAverage;

        // percentDiff = ((activity - average) / average) * 100
        // Positive = better for distance, duration, elevation
        // Negative = better for pace (lower pace = faster)
        double rawPercentDiff = ((activityValue - userAverage) / userAverage) * 100;

        switch (metricName) {
            case "pace":
                // Lower pace is better (faster), so negative diff = improvement
                comparison.percentDiff = -rawPercentDiff;
                comparison.isImprovement = comparison.percentDiff > 0;
                break;
            case "distance":
            case "duration":
            case "elevation":
                // Higher is better (or more effort), so positive diff = improvement
                comparison.percentDiff = rawPercentDiff;
                comparison.isImprovement = comparison.percentDiff > 0;
                break;
            default:
                comparison.percentDiff = rawPercentDiff;
                comparison.isImprovement = false;
                break;
        }

        return comparison;
    }

    /**
     * Holds pre-calculated user averages for a given activity type.
     */
    public static class UserAverages {
        public double averagePace;
        public double averageDistance;
        public double averageDuration;
        public double averageElevation;
        public int activityCount;
    }
}
