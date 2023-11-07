package com.javarush.jira.bugtracking.task;

import com.javarush.jira.bugtracking.Handlers;
import com.javarush.jira.bugtracking.task.to.ActivityTo;
import com.javarush.jira.common.error.DataConflictException;
import com.javarush.jira.login.AuthUser;
import com.javarush.jira.ref.RefType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.Comparator;
import java.util.List;

import static com.javarush.jira.bugtracking.task.TaskUtil.getLatestValue;

@Service
@RequiredArgsConstructor
public class ActivityService {
    private final TaskRepository taskRepository;
    private final ActivityRepository activityRepository;

    private final Handlers.ActivityHandler handler;

    private static void checkBelong(HasAuthorId activity) {
        if (activity.getAuthorId() != AuthUser.authId()) {
            throw new DataConflictException("Activity " + activity.getId() + " doesn't belong to " + AuthUser.get());
        }
    }

    @Transactional
    public Activity create(ActivityTo activityTo) {
        checkBelong(activityTo);
        Task task = taskRepository.getExisted(activityTo.getTaskId());
        if (activityTo.getStatusCode() != null) {
            task.checkAndSetStatusCode(activityTo.getStatusCode());
        }
        if (activityTo.getTypeCode() != null) {
            task.setTypeCode(activityTo.getTypeCode());
        }
        return handler.createFromTo(activityTo);
    }

    @Transactional
    public void update(ActivityTo activityTo, long id) {
        checkBelong(handler.getRepository().getExisted(activityTo.getId()));
        handler.updateFromTo(activityTo, id);
        updateTaskIfRequired(activityTo.getTaskId(), activityTo.getStatusCode(), activityTo.getTypeCode());
    }

    @Transactional
    public void delete(long id) {
        Activity activity = handler.getRepository().getExisted(id);
        checkBelong(activity);
        handler.delete(activity.id());
        updateTaskIfRequired(activity.getTaskId(), activity.getStatusCode(), activity.getTypeCode());
    }

    private void updateTaskIfRequired(long taskId, String activityStatus, String activityType) {
        if (activityStatus != null || activityType != null) {
            Task task = taskRepository.getExisted(taskId);
            List<Activity> activities = handler.getRepository().findAllByTaskIdOrderByUpdatedDesc(task.id());
            if (activityStatus != null) {
                String latestStatus = getLatestValue(activities, Activity::getStatusCode);
                if (latestStatus == null) {
                    throw new DataConflictException("Primary activity cannot be delete or update with null values");
                }
                task.setStatusCode(latestStatus);
            }
            if (activityType != null) {
                String latestType = getLatestValue(activities, Activity::getTypeCode);
                if (latestType == null) {
                    throw new DataConflictException("Primary activity cannot be delete or update with null values");
                }
                task.setTypeCode(latestType);
            }
        }
    }

    public long getTaskTimeInWork(long taskId) {
        List<Activity> activities = activityRepository.findAllByTaskId(taskId);
        activities.sort(Comparator.comparing(Activity::getUpdated));
        long totalTime = 0;
        LocalDateTime startTime = null;

        for (Activity activity : activities) {
            String statusCode = activity.getStatusCode();
            if ("in_progress".equals(statusCode)) {
                startTime = activity.getUpdated();
            }
            if ("ready_to_review".equals(statusCode) && startTime != null) {
                long timeSpent = Duration.between(startTime, activity.getUpdated()).toMillis();
                totalTime += timeSpent;
                startTime = null;
            }
        }
        return totalTime;
    }

    public long getTaskTimeInTest(long taskId) {
        List<Activity> activities = activityRepository.findAllByTaskId(taskId);
        activities.sort(Comparator.comparing(Activity::getUpdated));
        long totalTime = 0;
        LocalDateTime startTime = null;

        for (Activity activity : activities) {
            String statusCode = activity.getStatusCode();
            if ("ready_to_review".equals(statusCode)) {
                startTime = activity.getUpdated();
            }
            if ("done".equals(statusCode) && startTime != null) {
                long timeSpent = Duration.between(startTime, activity.getUpdated()).toMillis();
                totalTime += timeSpent;
                startTime = null;
            }
        }
        return totalTime;
    }
}
