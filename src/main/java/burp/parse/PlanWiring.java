package burp.parse;

import burp.model.*;
import burp.model.lv.LogicalView;
import burp.reporting.BurpException;
import burp.reporting.RmlExecutionReport;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

@NullMarked
public class PlanWiring {

    public static void wire(@Nullable PlanNode node) {
        wire(node, ValidationMode.STRICT, null);
    }

    public static void wire(@Nullable PlanNode node, @Nullable ValidationMode validationMode, @Nullable RmlExecutionReport report) {
        if (node == null) return;
        ValidationMode mode = validationMode != null ? validationMode : ValidationMode.STRICT;
        wireAll(node, Collections.newSetFromMap(new IdentityHashMap<>()), mode, report);
        wireDependencies(node);
    }

    private static void wireAll(PlanNode node, Set<PlanNode> visited, ValidationMode validationMode, @Nullable RmlExecutionReport report) {
        if (!visited.add(node)) return;
        wireParentChildren(node);
        wireLogicalViews(node);
        compileReferences(node, validationMode, report);
        for (PlanNode dep : node.dependencies()) {
            wireAll(dep, visited, validationMode, report);
        }
        for (PlanNode child : node.children()) {
            wireAll(child, visited, validationMode, report);
        }
    }

    private static void wireParentChildren(PlanNode node) {
        wireParentChildren(node, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private static void wireParentChildren(PlanNode node, Set<PlanNode> visited) {
        if (!visited.add(node)) return;
        node.children().forEach(child -> {
                child.setParent(node);
                wireParentChildren(child, visited);
        });
    }

    private static void wireLogicalViews(PlanNode node) {
        if (node instanceof LogicalView lv) lv.initialize();
        for (var lv : node.descendants(LogicalView.class).toList()) {
            lv.initialize();
        }
    }

    private static void wireDependencies(PlanNode node) {
        node.getDependents().clear();
        node.descendants(PlanNode.class).forEach(descendant -> descendant.getDependents().clear());
        wireDependenciesRecursive(node, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private static void wireDependenciesRecursive(PlanNode node, Set<PlanNode> visited) {
        if (!visited.add(node)) return;
        node.dependencies().forEach(dependency -> dependency.getDependents().add(node));

        if (node instanceof Expression) {
            TriplesMap triplesMap = node.ancestor(TriplesMap.class);
            if (triplesMap != null) {
                AbstractLogicalSource logicalSource = triplesMap.logicalSource;
                if (logicalSource != null) {
                    logicalSource.getDependents().add(node);
                }
            }
        }

        node.children().forEach(child -> wireDependenciesRecursive(child, visited));
    }

    private static void compileReferences(PlanNode node, ValidationMode validationMode, @Nullable RmlExecutionReport report) {
        if (node instanceof ReferenceHolder holder) {
            try {
                holder.compileReferences();
            } catch (BurpException be) {
                if (report != null && be.getError() != null && !report.getErrors().contains(be.getError())) {
                    report.getErrors().add(be.getError());
                }
                if (validationMode == ValidationMode.STRICT) {
                    throw be;
                }
            }
        }
    }
}
