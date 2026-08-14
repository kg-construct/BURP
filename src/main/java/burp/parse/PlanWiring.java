package burp.parse;

import burp.model.*;

import java.util.List;

public class PlanWiring {
    public static void wire(MappingDocument document) {
        // 1. Wire parent-children tree
        wireParentChildren(document);

        // 2. Optimize mapping order
        // TODO: merge the optimization from dev-mode branch

        // 3. Rewire parent-children tree
        wireParentChildren(document);

        // 4. Wire dependencies
        // wireDependencies(document);

        // 5. Compile References
        compileReferences(document);
    }

    private static void wireParentChildren(PlanNode node) {
        node.children().forEach(child -> {
            child.setParent(node);
            wireParentChildren(child);
        });
    }

    private static void wireDependencies(PlanNode node) {
        node.getDependents().clear();

        node.descendants(PlanNode.class).forEach(descendant -> descendant.getDependents().clear());

        wireDependenciesRecursive(node);
    }

    private static void wireDependenciesRecursive(PlanNode node) {
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

        node.children().forEach(PlanWiring::wireDependenciesRecursive);
    }

    private static void compileReferences(PlanNode node) {
        List<ReferenceHolder> holders = node.descendants(ReferenceHolder.class).toList();
        for (ReferenceHolder holder : holders) {
            holder.compileReferences();
        }
    }
}
