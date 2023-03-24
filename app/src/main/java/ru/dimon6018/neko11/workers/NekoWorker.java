package ru.dimon6018.neko11.workers;

import static ru.dimon6018.neko11.workers.Cat.PURR;
import static ru.dimon6018.neko11.ui.fragments.CatControlsFragment.randomWater;
import static ru.dimon6018.neko11.ui.fragments.NekoLand.CHAN_ID;
import static ru.dimon6018.neko11.ui.fragments.NekoLand.DEBUG_NOTIFICATIONS;
import ru.dimon6018.neko11.R;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import ru.dimon6018.neko11.NekoGeneralActivity;

public class NekoWorker extends Worker {

    private static final String TAG = "NekoWorker";
    public static int CAT_NOTIFICATION = 1;
    public static int DEBUG_NOTIFICATION = 1234;

    public static float CAT_CAPTURE_PROB = 1.02f; // generous

    public static long SECONDS = 1000;
    public static long MINUTES = 60 * SECONDS;

    public static float INTERVAL_JITTER_FRAC = 0.251f;

    public static String title_message;

    public NekoWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    public static void setupNotificationChannels(Context context) {
        NotificationManager noman = context.getSystemService(NotificationManager.class);
        NotificationChannel eggChan = new NotificationChannel(CHAN_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT);
        eggChan.setSound(Uri.EMPTY, Notification.AUDIO_ATTRIBUTES_DEFAULT);
        eggChan.setVibrationPattern(PURR);
        eggChan.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        noman.createNotificationChannel(eggChan);
    }

    @NonNull
    @Override
    public Result doWork() {
        Result state;
        Context context = getApplicationContext();
            try {
                triggerFoodResponse(context);
                state = Result.success();
            } catch (Exception e) {
                Log.e(TAG, "Error in worker method, see:" + e);
                state = Result.failure();
            }
            stopFoodWork();
            return state;
    }
    private static void triggerFoodResponse(Context context) {
        final PrefState prefs = new PrefState(context);
        int food = prefs.getFoodState();
        if (food != 0) {
            prefs.setFoodState(0); // nom
            if (Cat.RANDOM.nextFloat() <= CAT_CAPTURE_PROB) {
                Cat cat;
                List<Cat> cats = prefs.getCats();
                final int[] probs = context.getResources().getIntArray(R.array.food_new_cat_prob);
                final float waterLevel100 = prefs.getWaterState() / 2; // water is 0..200
                final float new_cat_prob = ((food < probs.length)
                        ? probs[food]
                        : waterLevel100) / 100f;
                Log.v("settings", "Food type: " + food);
                Log.v("settings", "New cat probability: " + new_cat_prob);

                if (cats.size() == 0 || Cat.RANDOM.nextFloat() <= new_cat_prob) {
                    title_message = context.getString(R.string.notification_title);
                    cat = newRandomCat(context, prefs);
                    prefs.setWaterState(prefs.getWaterState() - randomWater);
                    Log.v("settings", "A new cat is here: " + cat.getName());
                } else {
                    title_message = context.getString(R.string.notification_title_return);
                    cat = getExistingCat(prefs);
                    prefs.setWaterState(prefs.getWaterState() - randomWater);
                    Log.v("setings", "A cat has returned: " + cat.getName());
                }
                notifyCat(context, cat, title_message);
            }
        }
    }

    public static void notifyCat(Context context, Cat cat, String message) {
        title_message = message;
        NotificationManager noman = context.getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            final Notification.Builder builder = cat.buildNotificationP(context);
            noman.notify(cat.getShortcutId(), CAT_NOTIFICATION, builder.build());
        } else {
            final Notification.Builder builder = cat.buildNotificationO(context);
            noman.notify(cat.getShortcutId(), CAT_NOTIFICATION, builder.build());
        }

    }

    public static Cat newRandomCat(Context context, PrefState prefs) {
        final Cat cat = Cat.create(context);
        prefs.addCat(cat);
        return cat;
    }

    static Cat getExistingCat(PrefState prefs) {
        final List<Cat> cats = prefs.getCats();
        if (cats.size() == 0) return null;
        return cats.get(new Random().nextInt(cats.size()));
    }

    @Override
    public void onStopped() {
        super.onStopped();
    }

    public static void scheduleFoodWork(Context context, long intervalMinutes) {
        long interval = intervalMinutes * MINUTES;
        long jitter = (long) (INTERVAL_JITTER_FRAC * interval);
        interval += (long) (Math.random() * (2 * jitter)) - jitter;

        OneTimeWorkRequest workFoodRequest =
                new OneTimeWorkRequest.Builder(NekoWorker.class)
                        .addTag("FOODWORK")
                        .setInitialDelay(interval / MINUTES, TimeUnit.MINUTES)
                        .build();

        WorkManager.getInstance().enqueue(workFoodRequest);

        if (DEBUG_NOTIFICATIONS) {
            NotificationManager noman = context.getSystemService(NotificationManager.class);
            noman.notify(DEBUG_NOTIFICATION, new Notification.Builder(context)
                    .setSmallIcon(R.drawable.stat_icon)
                    .setContentTitle(String.format("Work scheduled in %d min", (interval / MINUTES)))
                    .setContentText("Work for food success scheduled")
                    .setPriority(Notification.PRIORITY_MIN)
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .setChannelId(CHAN_ID)
                    .setShowWhen(true)
                    .build());
        }
    }
    public static void stopFoodWork() {
        WorkManager.getInstance().cancelAllWorkByTag("FOODWORK");
    }
}
