package com.darexsh.appvault;

import java.io.File;
import java.util.List;

class BackupResult {
    final List<File> savedFiles;
    final boolean containsSplitApks;

    BackupResult(List<File> savedFiles, boolean containsSplitApks) {
        this.savedFiles = savedFiles;
        this.containsSplitApks = containsSplitApks;
    }
}
