package com.fanwe.lib.log;

import android.content.Context;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class FLogger
{
    static final Map<Class<?>, WeakReference<FLogger>> MAP_LOGGER = new ConcurrentHashMap<>();
    static final ReferenceQueue<FLogger> REFERENCE_QUEUE = new ReferenceQueue<>();
    static final Map<Class<?>, Class<?>> MAP_TAG = new HashMap<>();

    static Level sGlobalLevel;

    final Logger mLogger;

    SimpleFileHandler mFileHandler;
    int mLogFileLimit;
    Level mLogFileLevel;

    protected FLogger()
    {
        if (MAP_TAG.remove(getClass()) == null)
            throw new RuntimeException("you can not call this constructor");

        mLogger = Logger.getLogger(getClass().getName());
        mLogger.setLevel(sGlobalLevel);
    }

    /**
     * 日志对象被创建回调
     */
    protected abstract void onCreate();

    public synchronized static final <T extends FLogger> FLogger get(Class<T> clazz)
    {
        if (clazz == null)
            return null;
        if (clazz == FLogger.class)
            throw new IllegalArgumentException("clazz must not be " + FLogger.class);

        releaseIfNeed();

        FLogger logger = null;
        final WeakReference<FLogger> reference = MAP_LOGGER.get(clazz);
        if (reference != null)
        {
            logger = reference.get();
            if (logger != null)
                return logger;
        }

        try
        {
            MAP_TAG.put(clazz, clazz);
            logger = clazz.newInstance();
            if (MAP_TAG.containsKey(clazz))
                throw new RuntimeException("you must remove tag from tag map after logger instance created");

            logger.onCreate();
            MAP_LOGGER.put(clazz, new WeakReference<>(logger, REFERENCE_QUEUE));
            return logger;
        } catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    private static void releaseIfNeed()
    {
        while (true)
        {
            final Reference<? extends FLogger> reference = REFERENCE_QUEUE.poll();
            if (reference == null)
                break;

            for (Map.Entry<Class<?>, WeakReference<FLogger>> item : MAP_LOGGER.entrySet())
            {
                if (item.getValue() == reference)
                {
                    MAP_LOGGER.remove(item.getKey());
                    break;
                }
            }
        }
    }

    /**
     * 设置全局日志输出等级，小于设置等级的将不会被输出
     * <br>
     * 此方法需要在日志对象未被实例化之前调用
     *
     * @param level
     */
    public static final void setGlobalLevel(Level level)
    {
        if (!MAP_LOGGER.isEmpty())
            throw new RuntimeException("you can not call this method after logger instance created");

        sGlobalLevel = level;
    }

    /**
     * 删除所有日志文件
     */
    public static final void deleteAllLogFile()
    {
        for (Map.Entry<Class<?>, WeakReference<FLogger>> item : MAP_LOGGER.entrySet())
        {
            final FLogger logger = item.getValue().get();
            if (logger != null)
                logger.deleteLogFile();
        }
    }

    /**
     * {@link #openLogFile(int, Level, Context)}
     *
     * @param limitMB
     * @param context
     */
    public final void openLogFile(int limitMB, Context context)
    {
        openLogFile(limitMB, Level.INFO, context);
    }

    /**
     * 打开日志文件
     *
     * @param limitMB 文件大小限制(单位MB)
     * @param level   记录到文件的最小日志等级，小于指定等级的日志不会记录到文件
     * @param context
     */
    public synchronized final void openLogFile(int limitMB, Level level, Context context)
    {
        if (limitMB <= 0)
            throw new IllegalArgumentException("limitMB must greater than 0");

        final int max = Integer.MAX_VALUE / SimpleFileHandler.MB;
        if (limitMB > max)
            throw new IllegalArgumentException("limitMB must less than " + max);

        if (level == null)
            level = Level.ALL;

        if (mFileHandler == null
                || mLogFileLimit != limitMB || mLogFileLevel != level)
        {
            mLogFileLimit = limitMB;
            mLogFileLevel = level;
            closeLogFile();

            try
            {
                mFileHandler = new SimpleFileHandler(mLogger.getName() + ".log", limitMB * SimpleFileHandler.MB, context);
                mFileHandler.setLevel(level);

                mLogger.addHandler(mFileHandler);
            } catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * 关闭日志文件
     */
    public synchronized final void closeLogFile()
    {
        removeHandlers(mLogger);
        if (mFileHandler != null)
        {
            mFileHandler.close();
            mFileHandler = null;
        }
    }

    /**
     * 删除日志文件
     */
    public synchronized final void deleteLogFile()
    {
        if (mFileHandler != null)
            mFileHandler.deleteLogFile();
    }

    @Override
    protected void finalize() throws Throwable
    {
        super.finalize();
        closeLogFile();
    }

    //---------- log start ----------

    public final void info(String msg)
    {
        mLogger.info(msg);
    }

    public final void warning(String msg)
    {
        mLogger.warning(msg);
    }

    public final void severe(String msg)
    {
        mLogger.severe(msg);
    }

    public final void fine(String msg)
    {
        mLogger.fine(msg);
    }

    //---------- log end ----------

    //---------- utils start ----------

    private final static void removeHandlers(Logger logger)
    {
        final Handler[] handlers = logger.getHandlers();
        for (Handler item : handlers)
        {
            logger.removeHandler(item);
        }
    }

    //---------- utils end ----------
}
