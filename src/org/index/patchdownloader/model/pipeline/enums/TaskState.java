package org.index.patchdownloader.model.pipeline.enums;

/**
 * EN: Explicit lifecycle position of a single {@code FileDownloadTask}. {@code DONE} and
 *     {@code FAILED} are terminal; a terminal task must never re-enter a stage. {@code RETRY_WAIT}
 *     marks a task that failed a retryable download and is about to be re-submitted.<br>
 * RU: Явная позиция жизненного цикла одной задачи {@code FileDownloadTask}. {@code DONE} и
 *     {@code FAILED} — терминальные; терминальная задача не должна повторно входить в стадию.
 *     {@code RETRY_WAIT} помечает задачу, которая упала на повторяемой ошибке и будет отправлена заново.<br>
 **/
public enum TaskState
{
    CREATED(false, false),
    ACTIVE(false, true),
    RETRY_WAIT(false, false),
    DONE(true, false),
    FAILED(true, false);

    public static final TaskState[] VALUES = values();

    private final boolean _terminal;
    private final boolean _usable;

    TaskState(boolean terminal, boolean usable)
    {
        _terminal = terminal;
        _usable = usable;
    }

    /**
     * EN: Tells whether this is a terminal state ({@code DONE}/{@code FAILED}) from which no
     *     further transition is allowed. <br>
     * RU: Сообщает, является ли состояние терминальным ({@code DONE}/{@code FAILED}), из которого
     *     переходы запрещены. <br>
     * @return <br>
     *         {true}  - EN: terminal state, task is finished / RU: терминальное состояние, задача завершена <br>
     *         {false} - EN: non-terminal, task may still transition / RU: не терминальное, переход возможен <br>
     **/
    public boolean isTerminal()
    {
        return _terminal;
    }

    /**
     * EN: Tells whether the task is currently "in use" and may be processed by a stage. <br>
     * RU: Сообщает, находится ли задача в работе и может обрабатываться стадией. <br>
     * @return <br>
     *         {true}  - EN: task is active/usable / RU: задача активна/используется <br>
     *         {false} - EN: task is not usable in this state / RU: задача не используется в этом состоянии <br>
     **/
    public boolean isUsable()
    {
        return _usable;
    }
}
