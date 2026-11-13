package org.eclipse.core.runtime;
public interface IProgressMonitor {
    int UNKNOWN = -1;
    boolean isCanceled();
    void beginTask(String name, int totalWork);
    void subTask(String name);
    void worked(int work);
    void done();
    void setTaskName(String name);
}
