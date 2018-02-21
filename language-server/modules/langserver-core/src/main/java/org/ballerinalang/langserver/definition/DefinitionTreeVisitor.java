/*
 * Copyright (c) 2018, WSO2 Inc. (http://wso2.com) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ballerinalang.langserver.definition;

import org.ballerinalang.langserver.DocumentServiceKeys;
import org.ballerinalang.langserver.TextDocumentServiceContext;
import org.ballerinalang.langserver.common.NodeVisitor;
import org.ballerinalang.langserver.common.constants.NodeContextKeys;
import org.ballerinalang.model.tree.TopLevelNode;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.Symbols;
import org.wso2.ballerinalang.compiler.tree.BLangAction;
import org.wso2.ballerinalang.compiler.tree.BLangConnector;
import org.wso2.ballerinalang.compiler.tree.BLangFunction;
import org.wso2.ballerinalang.compiler.tree.BLangNode;
import org.wso2.ballerinalang.compiler.tree.BLangPackage;
import org.wso2.ballerinalang.compiler.tree.BLangResource;
import org.wso2.ballerinalang.compiler.tree.BLangService;
import org.wso2.ballerinalang.compiler.tree.BLangVariable;
import org.wso2.ballerinalang.compiler.tree.BLangWorker;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangLambdaFunction;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangSimpleVarRef;
import org.wso2.ballerinalang.compiler.tree.statements.BLangAssignment;
import org.wso2.ballerinalang.compiler.tree.statements.BLangBlockStmt;
import org.wso2.ballerinalang.compiler.tree.statements.BLangCatch;
import org.wso2.ballerinalang.compiler.tree.statements.BLangForeach;
import org.wso2.ballerinalang.compiler.tree.statements.BLangForkJoin;
import org.wso2.ballerinalang.compiler.tree.statements.BLangIf;
import org.wso2.ballerinalang.compiler.tree.statements.BLangTransaction;
import org.wso2.ballerinalang.compiler.tree.statements.BLangTryCatchFinally;
import org.wso2.ballerinalang.compiler.tree.statements.BLangVariableDef;
import org.wso2.ballerinalang.compiler.tree.statements.BLangWhile;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Tree visitor to find the definition of variables.
 */
public class DefinitionTreeVisitor extends NodeVisitor {

    private boolean terminateVisitor = false;
    private TextDocumentServiceContext context;
    private String fileName;

    public DefinitionTreeVisitor(TextDocumentServiceContext context) {
        this.context = context;
        this.fileName = context.get(DocumentServiceKeys.FILE_NAME_KEY);
        this.context.put(NodeContextKeys.NODE_KEY, null);
    }

    public void visit(BLangPackage pkgNode) {
        // Then visit each top-level element sorted using the compilation unit
        List<TopLevelNode> topLevelNodes = pkgNode.topLevelNodes.stream().filter(node ->
                node.getPosition().getSource().getCompilationUnitName().equals(this.fileName)
        ).collect(Collectors.toList());

        if (topLevelNodes.isEmpty()) {
            terminateVisitor = true;
            acceptNode(null);
        } else {
            topLevelNodes.forEach(topLevelNode -> acceptNode((BLangNode) topLevelNode));
        }
    }

    public void visit(BLangFunction funcNode) {
        // Check for native functions
        BSymbol funcSymbol = funcNode.symbol;
        if (Symbols.isNative(funcSymbol)) {
            return;
        }

        if (funcNode.name.getValue().equals(this.context.get(NodeContextKeys.NODE_OWNER_KEY))) {
            if (!funcNode.params.isEmpty()) {
                funcNode.params.forEach(this::acceptNode);
            }

            if (!funcNode.retParams.isEmpty()) {
                funcNode.retParams.forEach(this::acceptNode);
            }

            if (funcNode.body != null) {
                this.acceptNode(funcNode.body);
            }

            if (!funcNode.workers.isEmpty()) {
                funcNode.workers.forEach(this::acceptNode);
            }
        }
    }

    public void visit(BLangService serviceNode) {
        if (serviceNode.name.getValue()
                .equals(this.context.get(NodeContextKeys.NODE_OWNER_KEY))) {
            if (!serviceNode.resources.isEmpty()) {
                serviceNode.resources.forEach(this::acceptNode);
            }

            if (serviceNode.initFunction != null) {
                this.acceptNode(serviceNode.initFunction);
            }
        }
    }

    public void visit(BLangResource resourceNode) {
        if (resourceNode.name.getValue()
                .equals(this.context.get(NodeContextKeys.NODE_OWNER_KEY))) {
            if (!resourceNode.params.isEmpty()) {
                resourceNode.params.forEach(this::acceptNode);
            }

            if (!resourceNode.retParams.isEmpty()) {
                resourceNode.retParams.forEach(this::acceptNode);
            }

            if (resourceNode.body != null) {
                this.acceptNode(resourceNode.body);
            }
        }
    }

    public void visit(BLangConnector connectorNode) {
        if (connectorNode.name.getValue()
                .equals(this.context.get(NodeContextKeys.NODE_OWNER_KEY))) {
            if (!connectorNode.params.isEmpty()) {
                connectorNode.params.forEach(this::acceptNode);
            }

            if (!connectorNode.varDefs.isEmpty()) {
                connectorNode.varDefs.forEach(this::acceptNode);
            }

            if (!connectorNode.actions.isEmpty()) {
                connectorNode.actions.forEach(this::acceptNode);
            }
        }
    }

    public void visit(BLangAction actionNode) {
        if (actionNode.name.getValue()
                .equals(this.context.get(NodeContextKeys.NODE_OWNER_KEY))) {
            if (!actionNode.params.isEmpty()) {
                actionNode.params.forEach(this::acceptNode);
            }

            if (!actionNode.retParams.isEmpty()) {
                actionNode.retParams.forEach(this::acceptNode);
            }

            if (actionNode.body != null) {
                acceptNode(actionNode.body);
            }

            if (!actionNode.workers.isEmpty()) {
                actionNode.workers.forEach(this::acceptNode);
            }
        }
    }

    public void visit(BLangVariable varNode) {
        if (varNode.name.getValue()
                .equals(this.context.get(NodeContextKeys.VAR_NAME_OF_NODE_KEY))) {
            this.context.put(NodeContextKeys.NODE_KEY, varNode);
            terminateVisitor = true;
        }
    }

    public void visit(BLangWorker workerNode) {
        if (workerNode.body != null) {
            this.acceptNode(workerNode.body);
        }

        if (!workerNode.workers.isEmpty()) {
            workerNode.workers.forEach(this::acceptNode);
        }
    }

    public void visit(BLangBlockStmt blockNode) {
        if (!blockNode.stmts.isEmpty()) {
            blockNode.stmts.forEach(this::acceptNode);
        }
    }

    public void visit(BLangVariableDef varDefNode) {
        if (varDefNode.getVariable() != null) {
            this.acceptNode(varDefNode.getVariable());
        }
    }

    public void visit(BLangAssignment assignNode) {
        if (!assignNode.varRefs.isEmpty()) {
            assignNode.varRefs.forEach(this::acceptNode);
        }
    }

    public void visit(BLangIf ifNode) {
        if (ifNode.body != null) {
            this.acceptNode(ifNode.body);
        }

        if (ifNode.elseStmt != null) {
            this.acceptNode(ifNode.elseStmt);
        }
    }

    public void visit(BLangForeach foreach) {
        if (!foreach.varRefs.isEmpty()) {
            foreach.varRefs.forEach(this::acceptNode);
        }

        if (foreach.body != null) {
            this.acceptNode(foreach.body);
        }
    }

    public void visit(BLangWhile whileNode) {
        if (whileNode.body != null) {
            this.acceptNode(whileNode.body);
        }
    }

    public void visit(BLangTransaction transactionNode) {
        if (transactionNode.transactionBody != null) {
            this.acceptNode(transactionNode.transactionBody);
        }

        if (transactionNode.failedBody != null) {
            this.acceptNode(transactionNode.failedBody);
        }
    }

    public void visit(BLangTryCatchFinally tryNode) {
        if (tryNode.tryBody != null) {
            this.acceptNode(tryNode.tryBody);
        }

        if (!tryNode.catchBlocks.isEmpty()) {
            tryNode.catchBlocks.forEach(this::acceptNode);
        }

        if (tryNode.finallyBody != null) {
            this.acceptNode(tryNode.finallyBody);
        }
    }

    public void visit(BLangCatch catchNode) {
        if (catchNode.body != null) {
            this.acceptNode(catchNode.body);
        }
    }

    public void visit(BLangForkJoin forkJoin) {
        if (!forkJoin.getWorkers().isEmpty()) {
            forkJoin.getWorkers().forEach(this::acceptNode);
        }

        if (forkJoin.joinedBody != null) {
            this.acceptNode(forkJoin.joinedBody);
        }

        if (forkJoin.timeoutBody != null) {
            this.acceptNode(forkJoin.timeoutBody);
        }
    }

    public void visit(BLangSimpleVarRef varRefExpr) {
        if (varRefExpr.variableName.getValue()
                .equals(this.context.get(NodeContextKeys.VAR_NAME_OF_NODE_KEY))) {
            this.context.put(NodeContextKeys.NODE_KEY, varRefExpr);
            terminateVisitor = true;
        }
    }

    public void visit(BLangLambdaFunction bLangLambdaFunction) {
        if (bLangLambdaFunction.function != null) {
            this.acceptNode(bLangLambdaFunction.function);
        }
    }

    /**
     * Accept node to visit.
     *
     * @param node node to be accepted to visit.
     */
    private void acceptNode(BLangNode node) {
        if (this.terminateVisitor) {
            return;
        }
        node.accept(this);
    }
}
