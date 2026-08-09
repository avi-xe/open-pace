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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.openpace.activity.Activity;

/**
 * Service for aggregating user statistics.
 *
 * Calculates totals, averages, streaks, and personal record counts
 * across a user's activity history.
 */
@ApplicationScoped
public class StatsService {

    /**
     * Get statistics for a user.
     *
     * @param actorId the actor ID
     * @param period the time period filter: week, month, year, or all
     * @return aggregated user statistics
     */
    public UserStats getStats(Long actorId, String period) {
        List<Activity> activities = getActivitiesForPeriod(actorId, period);

        double totalDistance = 0;
        long totalTime = 0;

        for (Activity activity : activities) {
            totalDistance += extractDistance(activity);
            totalTime += extractDuration(activity);
        }

        int totalActivities = activities.size();
        double averagePace = 0;
        double averageDistance = 0;

        if (totalActivities > 0) {
            averageDistance = totalDistance / totalActivities;
        }
        if (totalDistance > 0 && totalTime > 0) {
            averagePace = (totalTime / totalDistance) * 1000; // seconds per km
        }

        int currentStreak = calculateCurrentStreak(activities);
        int bestStreak = calculateBestStreak(activities);
        long prCount = PersonalRecord.countByActor(actorId);

        UserStats stats = new UserStats();
        stats.period = period;
        stats.totalDistance = totalDistance;
        stats.totalTime = totalTime;
        stats.totalActivities = totalActivities;
        stats.averagePace = averagePace;
        stats.averageDistance = averageDistance;
        stats.currentStreak = currentStreak;
        stats.bestStreak = bestStreak;
        stats.prCount = prCount;

        return stats;
    }

    /**
     * Get activities for a period.
     *
     * @param actorId the actor ID
     * @param period the time period filter
     * @return list of activities within the period, ordered by publishedAt desc
     */
    private List<Activity> getActivitiesForPeriod(Long actorId, String period) {
        LocalDateTime startDate = getStartDate(period);
        if (startDate == null) {
            return Activity.find("actor.id = ?1 order by publishedAt desc", actorId).list();
        }
        return Activity.find("actor.id = ?1 and publishedAt >= ?2 order by publishedAt desc",
                actorId, startDate).list();
    }

    /**
     * Get start date for period.
     *
     * @param period the time period filter
     * @return start date, or null for all time
     */
    private LocalDateTime getStartDate(String period) {
        if (period == null) {
            return null;
        }
        return switch (period) {
            case "week" -> LocalDateTime.now().minusWeeks(1);
            case "month" -> LocalDateTime.now().minusMonths(1);
            case "year" -> LocalDateTime.now().minusYears(1);
            default -> null; // all time
        };
    }

    /**
     * Calculate current streak (consecutive days with activities from today backwards).
     *
     * @param activities list of activities
     * @return number of consecutive days
     */
    private int calculateCurrentStreak(List<Activity> activities) {
        if (activities.isEmpty()) {
            return 0;
        }

        Set<LocalDate> dates = getDistinctDates(activities);
        if (dates.isEmpty()) {
            return 0;
        }

        LocalDate today = LocalDate.now();
        int streak = 0;

        // Check if today or yesterday has an activity (grace period)
        LocalDate checkDate = today;
        if (!dates.contains(today) && !dates.contains(today.minusDays(1))) {
            return 0;
        }
        if (!dates.contains(today)) {
            checkDate = today.minusDays(1);
        }

        while (dates.contains(checkDate)) {
            streak++;
            checkDate = checkDate.minusDays(1);
        }

        return streak;
    }

    /**
     * Calculate best streak (longest consecutive run of activity days).
     *
     * @param activities list of activities
     * @return longest consecutive run in days
     */
    private int calculateBestStreak(List<Activity> activities) {
        if (activities.isEmpty()) {
            return 0;
        }

        Set<LocalDate> dates = getDistinctDates(activities);
        if (dates.isEmpty()) {
            return 0;
        }

        List<LocalDate> sorted = dates.stream()
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toList());

        int bestStreak = 1;
        int currentRun = 1;

        for (int i = 1; i < sorted.size(); i++) {
            long daysBetween = ChronoUnit.DAYS.between(sorted.get(i - 1), sorted.get(i));
            if (daysBetween == 1) {
                currentRun++;
            } else {
                bestStreak = Math.max(bestStreak, currentRun);
                currentRun = 1;
            }
        }

        return Math.max(bestStreak, currentRun);
    }

    /**
     * Get distinct activity dates, sorted descending.
     */
    private Set<LocalDate> getDistinctDates(List<Activity> activities) {
        Set<LocalDate> dates = new TreeSet<>(Comparator.reverseOrder());
        for (Activity activity : activities) {
            if (activity.publishedAt != null) {
                dates.add(activity.publishedAt.toLocalDate());
            }
        }
        return dates;
    }

    /**
     * Extract distance from activity track data.
     *
     * @param activity the activity
     * @return distance in meters, or 0 if not available
     */
    private double extractDistance(Activity activity) {
        if (activity.trackData == null) {
            return 0;
        }
        JsonNode summary = activity.trackData.get("summary");
        if (summary == null) {
            return 0;
        }
        JsonNode totalDistance = summary.get("totalDistance");
        if (totalDistance == null) {
            return 0;
        }
        return totalDistance.asDouble(0);
    }

    /**
     * Extract duration from activity track data.
     *
     * @param activity the activity
     * @return duration in seconds, or 0 if not available
     */
    private long extractDuration(Activity activity) {
        if (activity.trackData == null) {
            return 0;
        }
        JsonNode summary = activity.trackData.get("summary");
        if (summary == null) {
            return 0;
        }
        JsonNode totalDuration = summary.get("totalDuration");
        if (totalDuration == null) {
            return 0;
        }
        return totalDuration.asLong(0);
    }

    /**
     * DTO for stats response.
     */
    public static class UserStats {
        public String period;
        public double totalDistance;      // meters
        public long totalTime;           // seconds
        public int totalActivities;
        public double averagePace;       // seconds per km
        public double averageDistance;   // meters
        public int currentStreak;        // days
        public int bestStreak;           // days
        public long prCount;
    }
}
