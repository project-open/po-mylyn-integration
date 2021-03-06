package com.project_open.mylyn.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.mylyn.commons.core.StatusHandler;
import org.eclipse.mylyn.commons.net.AbstractWebLocation;
import org.eclipse.mylyn.tasks.core.IRepositoryListener;
import org.eclipse.mylyn.tasks.core.TaskRepository;
import org.eclipse.mylyn.tasks.core.TaskRepositoryLocationFactory;

import com.project_open.mylyn.core.client.ProjectOpenClient;
import com.project_open.mylyn.core.client.ProjectOpenClientData;
import com.project_open.mylyn.core.client.RestfulProjectOpenClient;

/**
 * @author Markus Knittig
 *
 */
public class ProjectOpenClientManager implements IRepositoryListener {

    private Map<String, ProjectOpenClient> clientByUrl = new HashMap<String, ProjectOpenClient>();

    private Map<String, ProjectOpenClientData> dataByUrl = new HashMap<String, ProjectOpenClientData>();

    private TaskRepositoryLocationFactory taskRepositoryLocationFactory;

    private File cacheFile;

    public ProjectOpenClientManager(File cacheFile) {
        this.cacheFile = cacheFile;
        readCache();
    }

    public synchronized ProjectOpenClient getClient(TaskRepository taskRepository) {
        String repositoryUrl = taskRepository.getRepositoryUrl();
        ProjectOpenClient repository = clientByUrl.get(repositoryUrl);

        if (repository == null) {
            AbstractWebLocation location =
                    taskRepositoryLocationFactory.createWebLocation(taskRepository);

            ProjectOpenClientData data = dataByUrl.get(repositoryUrl);
            if (data == null) {
                data = new ProjectOpenClientData();
                dataByUrl.put(repositoryUrl, data);
            }

            repository = new RestfulProjectOpenClient(location, data, taskRepository);
            clientByUrl.put(taskRepository.getRepositoryUrl(), repository);
        }

        return repository;
    }

    public TaskRepositoryLocationFactory getTaskRepositoryLocationFactory() {
        return taskRepositoryLocationFactory;
    }

    public void setTaskRepositoryLocationFactory(
            TaskRepositoryLocationFactory taskRepositoryLocationFactory) {
        this.taskRepositoryLocationFactory = taskRepositoryLocationFactory;
    }

    public void repositoryAdded(TaskRepository repository) {
        repositorySettingsChanged(repository);
    }

    public void repositoryRemoved(TaskRepository repository) {
        clientByUrl.remove(repository.getRepositoryUrl());
        dataByUrl.remove(repository.getRepositoryUrl());
    }

    public void repositorySettingsChanged(TaskRepository repository) {
        ProjectOpenClient client = clientByUrl.get(repository.getRepositoryUrl());

        if (client != null) {
            client.refreshRepositorySettings(repository);
        }
    }

    public void repositoryUrlChanged(TaskRepository repository, String oldUrl) {
        clientByUrl.put(repository.getRepositoryUrl(), clientByUrl.remove(oldUrl));
        dataByUrl.put(repository.getRepositoryUrl(), dataByUrl.remove(oldUrl));
    }

    private void readCache() {
        if (cacheFile == null || !cacheFile.exists()) {
            return;
        }

        ObjectInputStream in = null;
        try {
            in = new ObjectInputStream(new FileInputStream(cacheFile));

            for (int count = in.readInt(); count > 0; count--) {
                dataByUrl.put(in.readObject().toString(), (ProjectOpenClientData) in.readObject());
            }
        } catch (Throwable e) {
            StatusHandler.log(new Status(IStatus.WARNING, ProjectOpenCorePlugin.PLUGIN_ID,
                    "The ProjectOpen respository data cache could not be read", e));
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e1) {
                    // ignore
                }
            }
        }
    }

    void writeCache() {
        if (cacheFile == null) {
            return;
        }

        ObjectOutputStream out = null;
        try {
            out = new ObjectOutputStream(new FileOutputStream(cacheFile));

            out.writeInt(dataByUrl.size());
            for (Entry<String, ProjectOpenClientData> entry : dataByUrl.entrySet()) {
                out.writeObject(entry.getKey());
                out.writeObject(entry.getValue());
            }

            out.flush();
        } catch (Throwable e) {
            StatusHandler.log(new Status(IStatus.WARNING, ProjectOpenCorePlugin.PLUGIN_ID,
                    "The ProjectOpen respository data cache could not be written", e));
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e1) {
                    // ignore
                }
            }
        }
    }

}
