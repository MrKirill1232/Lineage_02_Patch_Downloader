package org.index.patchdownloader.model.pipeline.enums;

/**
 * EN: Which pipeline stage a task currently sits in. {@code COMPLETE} is the position after the
 *     last (store) stage finished. Ordinals are used as the linear stage order.<br>
 * RU: На какой стадии конвейера сейчас находится задача. {@code COMPLETE} — позиция после
 *     завершения последней стадии (store). Порядковые номера задают линейный порядок стадий.<br>
 **/
public enum TaskStage
{
    DOWNLOAD,
    DECOMPRESS,
    STORE,
    COMPLETE;

    public static final TaskStage[] VALUES = values();

    /**
     * EN: Returns the next stage in the linear pipeline order, or {@code COMPLETE} when already at
     *     the end. Used by {@code AbstractFileRequest.advanceStage()}. <br>
     * RU: Возвращает следующую стадию в линейном порядке конвейера либо {@code COMPLETE}, если уже
     *     в конце. Используется в {@code AbstractFileRequest.advanceStage()}. <br>
     * @return <br>
     *         {TaskStage} - EN: the next stage (clamped to COMPLETE) / RU: следующая стадия (не выше COMPLETE) <br>
     **/
    public TaskStage next()
    {
        int nextOrdinal = ordinal() + 1;
        if (nextOrdinal >= VALUES.length)
        {
            return COMPLETE;
        }
        return VALUES[nextOrdinal];
    }
}
