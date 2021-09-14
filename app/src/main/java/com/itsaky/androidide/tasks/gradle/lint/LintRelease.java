package com.itsaky.androidide.tasks.gradle.lint;

import com.itsaky.androidide.R;
import com.itsaky.androidide.app.StudioApp;
import com.itsaky.androidide.services.IDEService;
import com.itsaky.androidide.tasks.BaseGradleTask;
import java.io.File;
import java.util.Arrays;
import java.util.List;

public class LintRelease extends BaseGradleTask {
    
	@Override
	public String getName() {
		return StudioApp.getInstance().getString(R.string.lint_release);
	}
    
	@Override
	public String getCommands() {
		return "lintRelease";
	}
    
    @Override
    public List<String> getTasks() {
        return Arrays.asList("lintRelease");
    }

	@Override
	public int getTaskID() {
		return IDEService.TASK_LINT;
	}

	@Override
	public boolean canOutput() {
		return true;
	}

	@Override
	public boolean buildsApk() {
		return false;
	}

	@Override
	public File getApk(String buildFolder, String moduleName) {
		return null;
	}

	@Override
	public Type getType() {
		return Type.LINT;
	}
}
