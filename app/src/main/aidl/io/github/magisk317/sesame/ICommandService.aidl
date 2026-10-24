package io.github.magisk317.sesame;

import io.github.magisk317.sesame.ICallback;
import io.github.magisk317.sesame.IStatusListener;

interface ICommandService {
    void executeCommand(String command, ICallback callback);
    void registerListener(IStatusListener listener);
    void unregisterListener(IStatusListener listener);
}
