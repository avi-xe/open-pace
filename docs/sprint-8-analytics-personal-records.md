# Sprint 8: Activity Analytics & Personal Records

## Metadata
- **Sprint Number**: 8
- **Estimated Time**: 8-10 hours
- **Complexity**: High
- **Dependencies**: Sprint 7.5 (Consolidation)

## Objectives

1. Implement automatic personal record detection
2. Add split analysis (per km/mile)
3. Create pace zones and heart rate zones
4. Build elevation profile visualization
5. Implement comparative analysis
6. Create activity statistics dashboard

## Features

### 1. Personal Records (PRs)

**What It Does**:
- Automatically detect PRs when activity is created
- Track fastest times for common distances
- Track longest activities by type
- Store PR history (not just current best)

**PR Types to Track**:
| Category | Distances |
|----------|-----------|
| **Run** | 1K, 5K, 10K, Half Marathon, Marathon |
| **Ride** | 20K, 50K, 100K, 200K |
| **Swim** | 400m, 1500m, 5K |
| **Walk** | 5K, 10K, 20K |
| **Hike** | 10K, 20K, 50K |

**Storage**:
```java
@Entity
public class PersonalRecord {
    @Id @GeneratedValue
    public Long id;
    
    public Actor actor;
    public ActivityType activityType;
    public String distanceLabel;  // "5K", "10K", etc.
    public double distanceMeters; // Actual distance threshold
    public long elapsedTime;      // Best time in seconds
    public Activity activity;     // The activity where PR was set
    public LocalDateTime achievedAt;
}
```

**Algorithm**:
1. When activity is saved, calculate total distance
2. Find matching PR distance thresholds
3. For each threshold, check if activity covers it
4. Extract segment time (start to threshold point)
5. Compare with existing PR
6. Update if new record

### 2. Split Analysis

**What It Does**:
- Break activity into km/mile splits
- Show pace per split
- Show elevation per split
- Highlight fastest/slowest splits

**Storage**:
```java
@Entity
public class ActivitySplit {
    @Id @GeneratedValue
    public Long id;
    
    public Activity activity;
    public int splitNumber;      // 1, 2, 3, etc.
    public double distanceMeters; // Split distance
    public long elapsedTime;      // Split time
    public double pace;           // Seconds per km/mile
    public double elevationGain;
    public double elevationLoss;
    public double averageHeartRate; // If available
}
```

**Calculation**:
1. Iterate through track points
2. Accumulate distance
3. When distance reaches split threshold (1km or 1mi):
   - Calculate split time
   - Calculate split pace
   - Store split
   - Reset accumulators

### 3. Pace Zones

**What It Does**:
- Categorize effort into pace zones
- Show time in each zone
- Visualize zone distribution

**Zone Definitions (Run)**:
| Zone | Pace Range | Description |
|------|------------|-------------|
| 1 | < 5:00/km | Recovery |
| 2 | 5:00-4:30/km | Easy |
| 3 | 4:30-4:00/km | Tempo |
| 4 | 4:00-3:30/km | Threshold |
| 5 | > 3:30/km | VO2 Max |

**Storage**:
```java
@Entity
public class ActivityPaceZone {
    @Id @GeneratedValue
    public Long id;
    
    public Activity activity;
    public int zoneNumber;        // 1-5
    public long timeInSeconds;    // Time in this zone
    public double percentage;     // % of total time
}
```

### 4. Elevation Profile

**What It Does**:
- Generate elevation data for charts
- Show elevation gain/loss per km
- Identify climbs and descents

**Data Format** (for API):
```json
{
  "elevationProfile": [
    { "distance": 0, "elevation": 100 },
    { "distance": 1000, "elevation": 120 },
    { "distance": 2000, "elevation": 95 }
  ],
  "totalGain": 450,
  "totalLoss": 420,
  "maxElevation": 250,
  "minElevation": 80
}
```

### 5. Comparative Analysis

**What It Does**:
- Compare current activity to user's average
- Show improvement/decline
- Highlight best performances

**Comparisons**:
| Metric | Comparison |
|--------|------------|
| Pace | vs. user's average pace |
| Distance | vs. user's average distance |
| Duration | vs. user's average duration |
| Elevation | vs. user's average elevation |
| Splits | vs. user's average splits |

**Storage**:
```java
@Entity
public class ActivityComparison {
    @Id @GeneratedValue
    public Long id;
    
    public Activity activity;
    public String metricName;     // "pace", "distance", etc.
    public double activityValue;
    public double userAverage;
    public double percentDiff;    // Positive = better
    public boolean isImprovement;
}
```

### 6. Statistics Dashboard

**What It Does**:
- Aggregate stats across all activities
- Show trends over time
- Compare periods (this week vs. last week)

**Stats to Calculate**:
| Stat | Calculation |
|------|-------------|
| Total Distance | Sum of all activities |
| Total Time | Sum of all durations |
| Total Activities | Count |
| Average Pace | Weighted average |
| Average Distance | Sum / count |
| Longest Activity | Max duration |
| Farthest Activity | Max distance |
| Current Streak | Consecutive days with activity |
| Best Streak | Longest consecutive days |

**API Endpoint**:
```
GET /users/{username}/stats?period=month
```

**Response**:
```json
{
  "period": "month",
  "totalDistance": 125000,
  "totalTime": 14400,
  "totalActivities": 12,
  "averagePace": 300,
  "currentStreak": 5,
  "bestStreak": 14,
  "prCount": 3
}
```

## Implementation Plan

### Phase 1: Database Schema (1 hour)

1. Create `personal_record` table
2. Create `activity_split` table
3. Create `activity_pace_zone` table
4. Create `activity_comparison` table
5. Add indexes for performance

**Migration**:
```sql
CREATE TABLE personal_record (
    id BIGSERIAL PRIMARY KEY,
    actor_id BIGINT REFERENCES actor(id),
    activity_type VARCHAR(50) NOT NULL,
    distance_label VARCHAR(20) NOT NULL,
    distance_meters DOUBLE PRECISION NOT NULL,
    elapsed_time BIGINT NOT NULL,
    activity_id BIGINT REFERENCES activity(id),
    achieved_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_pr_actor_type ON personal_record(actor_id, activity_type);
CREATE UNIQUE INDEX idx_pr_unique ON personal_record(actor_id, activity_type, distance_label);
```

### Phase 2: PR Detection (2 hours)

1. Implement PR calculation logic
2. Add PR detection to ActivityService
3. Store PRs on activity creation
4. Handle tie-breaking (earlier activity wins)

**Files**:
- `src/main/java/org/openpace/analytics/PersonalRecordService.java` (new)
- `src/main/java/org/openpace/analytics/PersonalRecord.java` (new)
- Update `ActivityService.java`

### Phase 3: Split Analysis (2 hours)

1. Implement split calculation
2. Store splits on activity creation
3. Add splits to activity response

**Files**:
- `src/main/java/org/openpace/analytics/SplitService.java` (new)
- `src/main/java/org/openpace/analytics/ActivitySplit.java` (new)
- Update `ActivityService.java`

### Phase 4: Pace Zones (1 hour)

1. Implement pace zone calculation
2. Store zones on activity creation
3. Add zones to activity response

**Files**:
- `src/main/java/org/openpace/analytics/PaceZoneService.java` (new)
- `src/main/java/org/openpace/analytics/ActivityPaceZone.java` (new)

### Phase 5: Comparative Analysis (1 hour)

1. Implement comparison logic
2. Calculate user averages
3. Store comparisons on activity creation

**Files**:
- `src/main/java/org/openpace/analytics/ComparisonService.java` (new)
- `src/main/java/org/openpace/analytics/ActivityComparison.java` (new)

### Phase 6: Statistics Dashboard (1 hour)

1. Implement aggregation queries
2. Create statistics endpoint
3. Add caching for performance

**Files**:
- `src/main/java/org/openpace/analytics/StatsService.java` (new)
- `src/main/java/org/openpace/analytics/StatsResource.java` (new)
- `src/main/resources/META-INF/resources/index.html` (update dashboard)

### Phase 7: Testing (1 hour)

1. Unit tests for all calculation logic
2. Integration tests for API endpoints
3. Manual testing with real activities

## API Endpoints

### PR Endpoints
```
GET /users/{username}/prs                    # List all PRs
GET /users/{username}/prs?activityType=Run   # PRs for specific type
GET /users/{username}/prs/5K                 # PR for specific distance
```

### Split Endpoints
```
GET /activities/{id}/splits                  # Get splits for activity
```

### Stats Endpoints
```
GET /users/{username}/stats                  # All-time stats
GET /users/{username}/stats?period=week      # This week
GET /users/{username}/stats?period=month     # This month
GET /users/{username}/stats?period=year      # This year
```

## Acceptance Criteria

### Personal Records
- [ ] PRs detected automatically on activity creation
- [ ] PRs stored with activity reference
- [ ] PR history maintained (not just current best)
- [ ] Tie-breaking: earlier activity wins

### Split Analysis
- [ ] Splits calculated for all activities with track data
- [ ] Both km and mile splits supported
- [ ] Fastest/slowest splits highlighted
- [ ] Elevation per split included

### Pace Zones
- [ ] Zones calculated based on activity type
- [ ] Time in each zone recorded
- [ ] Zone distribution visualized

### Comparative Analysis
- [ ] Activity compared to user's average
- [ ] Improvement/decline calculated
- [ ] Best performances highlighted

### Statistics Dashboard
- [ ] All-time stats calculated
- [ ] Period-based stats (week/month/year)
- [ ] Current streak calculated
- [ ] Best streak calculated

### Performance
- [ ] PR detection adds <100ms to activity creation
- [ ] Stats endpoint responds in <500ms
- [ ] Database queries optimized with indexes

## Testing Strategy

### Unit Tests
- PR calculation logic
- Split calculation
- Pace zone calculation
- Comparison calculation
- Streak calculation

### Integration Tests
- Full activity creation with analytics
- Stats endpoint with various periods
- PR detection with multiple activities

### Manual Testing
- Create activities with track data
- Verify PRs detected correctly
- Verify splits calculated correctly
- Verify stats dashboard shows correct data

## Out of Scope

- Heart rate zones (requires HR data)
- Power analysis (requires power meter)
- Advanced charts (Sprint 16)
- Mobile app analytics (Sprint 18)

## References

- [Strava API - Activity Stats](https://developers.strava.com/docs/reference/#api-Stats)
- [Strava API - Activity Laps](https://developers.strava.com/docs/reference/#api-Activities-getActivityLaps)
- [Running Pace Zones](https://www.trainingpeaks.com/blog/running-pace-zones/)
