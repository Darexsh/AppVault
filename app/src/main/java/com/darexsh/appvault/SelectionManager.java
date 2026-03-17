package com.darexsh.appvault;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

class SelectionManager {
    private final Set<String> expandedPackages = new HashSet<>();
    private final Set<String> selectedPackages = new HashSet<>();
    private boolean selectionMode = false;

    boolean isSelectionMode() {
        return selectionMode;
    }

    boolean isExpanded(String packageName) {
        return expandedPackages.contains(packageName);
    }

    boolean isSelected(String packageName) {
        return selectedPackages.contains(packageName);
    }

    Set<String> getSelectedPackages() {
        return selectedPackages;
    }

    int getSelectedCount() {
        return selectedPackages.size();
    }

    boolean hasSelection() {
        return !selectedPackages.isEmpty();
    }

    boolean toggleExpanded(String packageName) {
        if (expandedPackages.contains(packageName)) {
            expandedPackages.remove(packageName);
            return false;
        }
        expandedPackages.add(packageName);
        return true;
    }

    void toggleSelection(String packageName) {
        if (selectedPackages.contains(packageName)) {
            selectedPackages.remove(packageName);
        } else {
            selectedPackages.add(packageName);
        }
        if (selectedPackages.isEmpty()) {
            selectionMode = false;
        }
    }

    void selectAll(List<AppRow> rows) {
        selectedPackages.clear();
        for (AppRow row : rows) {
            selectedPackages.add(row.packageInfo.packageName);
        }
        selectionMode = !selectedPackages.isEmpty();
    }

    void enterSelectionAndSelect(String packageName) {
        if (!selectionMode) {
            selectionMode = true;
            selectedPackages.clear();
        }
        selectedPackages.add(packageName);
    }

    void exitSelectionMode() {
        selectionMode = false;
        selectedPackages.clear();
    }

    void pruneToExistingPackages(Set<String> existingPackages) {
        Iterator<String> expandedIterator = expandedPackages.iterator();
        while (expandedIterator.hasNext()) {
            if (!existingPackages.contains(expandedIterator.next())) {
                expandedIterator.remove();
            }
        }

        Iterator<String> selectedIterator = selectedPackages.iterator();
        while (selectedIterator.hasNext()) {
            if (!existingPackages.contains(selectedIterator.next())) {
                selectedIterator.remove();
            }
        }

        if (selectionMode && selectedPackages.isEmpty()) {
            selectionMode = false;
        }
    }
}
