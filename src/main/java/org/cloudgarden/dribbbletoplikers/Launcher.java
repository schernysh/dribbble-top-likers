package org.cloudgarden.dribbbletoplikers;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import lombok.RequiredArgsConstructor;
import org.cloudgarden.dribbbletoplikers.api.DribbbleApi;
import org.cloudgarden.dribbbletoplikers.model.FollowerEntry;
import org.cloudgarden.dribbbletoplikers.model.Like;
import org.cloudgarden.dribbbletoplikers.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveTask;
import java.util.function.IntFunction;
import java.util.stream.IntStream;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.*;

public class Launcher {

    private static final Logger log = LoggerFactory.getLogger(Launcher.class);

    @Parameter(names = "-clientAccessToken", required = true)
    private String clientAccessToken;

    @Parameter(names = "-userId", required = true)
    private String userId;

    @Parameter(names = "-topNum")
    private int topNum = 10;

    public static void main(String[] args) {
        final Launcher launcher = new Launcher();
        try {
            JCommander.newBuilder().addObject(launcher).build().parse(args);
        } catch (ParameterException e) {
            log.error("Could not start the application due to incomplete configuration: {}", e.getMessage());
            System.exit(253);
        }
        launcher.run();
    }

    private void run() {
        try {
            final DribbbleApi api = DribbbleApi.connect("https://api.dribbble.com/v1", clientAccessToken);

            final Map<String, Long> likers = new ForkJoinPool(10).invoke(new UserTask(api, userId));

            log.info("Top {} likers:", topNum);

            likers.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(topNum)
                    .forEach(e -> log.info("{}: {}", e.getKey(), e.getValue()));
        } catch (Exception e) {
            log.error("Unexpected exception occurred:", e);
            System.exit(255);
        }
    }

    @RequiredArgsConstructor
    private abstract class AbstractTask extends RecursiveTask<Map<String, Long>> {
        final DribbbleApi api;
        final int pageSize = 100;

        @Override
        protected Map<String, Long> compute() {
            return invokeAll(computeAndCreateSubtasks()).stream()
                    .map(ForkJoinTask::join)
                    .map(Map::entrySet)
                    .flatMap(Collection::stream)
                    .collect(toMap(Map.Entry::getKey, Map.Entry::getValue, Long::sum));
        }

        abstract List<AbstractTask> computeAndCreateSubtasks();

        List<AbstractTask> createTaskPerPage(int totalSize, IntFunction<AbstractTask> taskCreator) {
            return IntStream.rangeClosed(1, (totalSize / pageSize) + 1).mapToObj(taskCreator).collect(toList());
        }
    }

    private class UserTask extends AbstractTask {
        private final String userId;

        private UserTask(DribbbleApi api, String userId) {
            super(api);
            this.userId = userId;
        }

        @Override
        List<AbstractTask> computeAndCreateSubtasks() {
            return createTaskPerPage(api.getUser(userId).getFollowersCount(), page -> new FollowersTask(api, userId, page));
        }
    }

    private class FollowersTask extends AbstractTask {
        private final String userId;
        private final int page;

        private FollowersTask(DribbbleApi api, String userId, int page) {
            super(api);
            this.userId = userId;
            this.page = page;
        }

        @Override
        List<AbstractTask> computeAndCreateSubtasks() {
            return api.getFollowers(userId, pageSize, page).stream()
                    .map(FollowerEntry::getFollower)
                    .flatMap(follower ->
                            createTaskPerPage(follower.getShotsCount(), page -> new ShotsTask(api, follower.getUsername(), page)).stream())
                    .collect(toList());
        }
    }

    private class ShotsTask extends AbstractTask {
        private final String userId;
        private final int page;

        private ShotsTask(DribbbleApi api, String userId, int page) {
            super(api);
            this.userId = userId;
            this.page = page;
        }

        @Override
        List<AbstractTask> computeAndCreateSubtasks() {
            return api.getShots(userId, pageSize, page).stream()
                    .flatMap(shot ->
                            createTaskPerPage(shot.getLikesCount(), page -> new LikesTask(api, shot.getId(), page)).stream())
                    .collect(toList());
        }
    }

    private class LikesTask extends AbstractTask {
        private final Long shotId;
        private final int page;

        private LikesTask(DribbbleApi api, Long shotId, int page) {
            super(api);
            this.shotId = shotId;
            this.page = page;
        }

        @Override
        protected Map<String, Long> compute() {
            return api.getLikes(shotId, pageSize, page).stream()
                    .map(Like::getUser)
                    .map(User::getUsername)
                    .collect(groupingBy(identity(), counting()));
        }

        @Override
        List<AbstractTask> computeAndCreateSubtasks() {
            return null;
        }
    }
}
