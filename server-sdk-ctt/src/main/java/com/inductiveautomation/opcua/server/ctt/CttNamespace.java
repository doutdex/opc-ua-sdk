/*
 * Copyright 2014 Inductive Automation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.inductiveautomation.opcua.server.ctt;

import java.lang.reflect.Array;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.PeekingIterator;
import com.inductiveautomation.opcua.sdk.core.AccessLevel;
import com.inductiveautomation.opcua.sdk.core.Reference;
import com.inductiveautomation.opcua.sdk.core.ValueRank;
import com.inductiveautomation.opcua.sdk.server.OpcUaServer;
import com.inductiveautomation.opcua.sdk.server.api.DataItem;
import com.inductiveautomation.opcua.sdk.server.api.MethodInvocationHandler;
import com.inductiveautomation.opcua.sdk.server.api.MonitoredItem;
import com.inductiveautomation.opcua.sdk.server.api.UaNamespace;
import com.inductiveautomation.opcua.sdk.server.model.UaFolderNode;
import com.inductiveautomation.opcua.sdk.server.model.UaMethodNode;
import com.inductiveautomation.opcua.sdk.server.model.UaNode;
import com.inductiveautomation.opcua.sdk.server.model.UaObjectNode;
import com.inductiveautomation.opcua.sdk.server.model.UaVariableNode;
import com.inductiveautomation.opcua.sdk.server.model.UaVariableNode.UaVariableNodeBuilder;
import com.inductiveautomation.opcua.sdk.server.util.AnnotationBasedInvocationHandler;
import com.inductiveautomation.opcua.sdk.server.util.SubscriptionModel;
import com.inductiveautomation.opcua.server.ctt.methods.SqrtMethod;
import com.inductiveautomation.opcua.stack.core.Identifiers;
import com.inductiveautomation.opcua.stack.core.StatusCodes;
import com.inductiveautomation.opcua.stack.core.UaException;
import com.inductiveautomation.opcua.stack.core.types.builtin.ByteString;
import com.inductiveautomation.opcua.stack.core.types.builtin.DataValue;
import com.inductiveautomation.opcua.stack.core.types.builtin.DateTime;
import com.inductiveautomation.opcua.stack.core.types.builtin.ExpandedNodeId;
import com.inductiveautomation.opcua.stack.core.types.builtin.LocalizedText;
import com.inductiveautomation.opcua.stack.core.types.builtin.NodeId;
import com.inductiveautomation.opcua.stack.core.types.builtin.QualifiedName;
import com.inductiveautomation.opcua.stack.core.types.builtin.StatusCode;
import com.inductiveautomation.opcua.stack.core.types.builtin.Variant;
import com.inductiveautomation.opcua.stack.core.types.builtin.XmlElement;
import com.inductiveautomation.opcua.stack.core.types.builtin.unsigned.UInteger;
import com.inductiveautomation.opcua.stack.core.types.builtin.unsigned.UShort;
import com.inductiveautomation.opcua.stack.core.types.enumerated.NodeClass;
import com.inductiveautomation.opcua.stack.core.types.enumerated.TimestampsToReturn;
import com.inductiveautomation.opcua.stack.core.types.structured.ReadValueId;
import com.inductiveautomation.opcua.stack.core.types.structured.WriteValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.inductiveautomation.opcua.stack.core.types.builtin.unsigned.Unsigned.ubyte;
import static com.inductiveautomation.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;
import static com.inductiveautomation.opcua.stack.core.types.builtin.unsigned.Unsigned.ulong;
import static com.inductiveautomation.opcua.stack.core.types.builtin.unsigned.Unsigned.ushort;

public class CttNamespace implements UaNamespace {

    public static final String NAMESPACE_URI = "urn:inductiveautomation:ctt-namespace";

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Map<NodeId, UaNode> nodes = Maps.newConcurrentMap();

    private final UaFolderNode cttFolder;
    private final SubscriptionModel subscriptionModel;

    private final OpcUaServer server;
    private final UShort namespaceIndex;

    public CttNamespace(OpcUaServer server, UShort namespaceIndex) {
        this.server = server;
        this.namespaceIndex = namespaceIndex;

        NodeId cttNodeId = new NodeId(namespaceIndex, "CTT");

        cttFolder = new UaFolderNode(
                this,
                cttNodeId,
                new QualifiedName(namespaceIndex, "CTT"),
                LocalizedText.english("CTT")
        );

        nodes.put(cttNodeId, cttFolder);

        try {
            server.getUaNamespace().addReference(
                    Identifiers.ObjectsFolder,
                    Identifiers.Organizes,
                    true, server.getServerTable().getUri(0),
                    cttNodeId.expanded(), NodeClass.Object);
        } catch (UaException e) {
            logger.error("Error adding reference to Connections folder.", e);
        }

        subscriptionModel = new SubscriptionModel(this, server.getExecutorService(), server.getScheduledExecutorService());

        addStaticScalarNodes();
        addStaticArrayNodes();
        addMethodNodes();
    }


    private static final Object[][] StaticScalarNodes = new Object[][]{
            {"Bool", Identifiers.Boolean, new Variant(false)},
            {"Byte", Identifiers.Byte, new Variant(ubyte(0x00))},
            {"ByteString", Identifiers.ByteString, new Variant(new ByteString(new byte[]{0x01, 0x02, 0x03, 0x04}))},
            {"DateTime", Identifiers.DateTime, new Variant(DateTime.now())},
            {"Double", Identifiers.Double, new Variant(3.14d)},
            {"Float", Identifiers.Float, new Variant(3.14f)},
            {"Guid", Identifiers.Guid, new Variant(UUID.randomUUID())},
            {"Int16", Identifiers.Int16, new Variant((short) 16)},
            {"Int32", Identifiers.Int32, new Variant(32)},
            {"Int64", Identifiers.Int64, new Variant(64L)},
            {"LocalizedText", Identifiers.LocalizedText, new Variant(LocalizedText.english("localized text"))},
            {"NodeId", Identifiers.NodeId, new Variant(new NodeId(1234, "abcd"))},
            {"QualifiedName", Identifiers.QualifiedName, new Variant(new QualifiedName(1234, "defg"))},
            {"SByte", Identifiers.SByte, new Variant((byte) 0x00)},
            {"String", Identifiers.String, new Variant("string value")},
            {"UtcTime", Identifiers.UtcTime, new Variant(DateTime.now())},
            {"UInt16", Identifiers.UInt16, new Variant(ushort(16))},
            {"UInt32", Identifiers.UInt32, new Variant(uint(32))},
            {"UInt64", Identifiers.UInt64, new Variant(ulong(64L))},
            {"XmlElement", Identifiers.XmlElement, new Variant(new XmlElement("<a>hello</a>"))},
    };

    private void addStaticScalarNodes() {
        UaObjectNode folder = addFoldersToRoot(cttFolder, "/Static/AllProfiles/Scalar");

        for (Object[] os : StaticScalarNodes) {
            String name = (String) os[0];
            NodeId typeId = (NodeId) os[1];
            Variant variant = (Variant) os[2];

            UaVariableNode node = new UaVariableNodeBuilder(this)
                    .setNodeId(new NodeId(namespaceIndex, "/Static/AllProfiles/Scalar/" + name))
                    .setAccessLevel(ubyte(AccessLevel.getMask(AccessLevel.READ_WRITE)))
                    .setBrowseName(new QualifiedName(namespaceIndex, name))
                    .setDisplayName(LocalizedText.english(name))
                    .setDataType(typeId)
                    .setTypeDefinition(Identifiers.BaseDataVariableType)
                    .build();

            node.setValue(new DataValue(variant));

            folder.addReference(new Reference(
                    folder.getNodeId(),
                    Identifiers.Organizes,
                    node.getNodeId().expanded(),
                    node.getNodeClass(),
                    true
            ));

            logger.debug("Added reference: {} -> {}", folder.getNodeId(), node.getNodeId());

            nodes.put(node.getNodeId(), node);
        }
    }

    private static final Object[][] StaticArrayNodes = new Object[][]{
            {"Bool", Identifiers.Boolean, false},
            {"Byte", Identifiers.Byte, ubyte(0)},
            {"ByteString", Identifiers.ByteString, new ByteString(new byte[]{0x01, 0x02, 0x03, 0x04})},
            {"DateTime", Identifiers.DateTime, DateTime.now()},
            {"Double", Identifiers.Double, 3.14d},
            {"Float", Identifiers.Float, 3.14f},
            {"Guid", Identifiers.Guid, UUID.randomUUID()},
            {"Int16", Identifiers.Int16, (short) 16},
            {"Int32", Identifiers.Int32, 32},
            {"Int64", Identifiers.Int64, 64L},
            {"LocalizedText", Identifiers.LocalizedText, LocalizedText.english("localized text")},
            {"NodeId", Identifiers.NodeId, new NodeId(1234, "abcd")},
            {"QualifiedName", Identifiers.QualifiedName, new QualifiedName(1234, "defg")},
            {"SByte", Identifiers.SByte, (byte) 0x00},
            {"String", Identifiers.String, "string value"},
            {"UtcTime", Identifiers.UtcTime, DateTime.now()},
            {"UInt16", Identifiers.UInt16, ushort(16)},
            {"UInt32", Identifiers.UInt32, uint(32)},
            {"UInt64", Identifiers.UInt64, ulong(64L)},
            {"XmlElement", Identifiers.XmlElement, new XmlElement("<a>hello</a>")},
    };

    private void addStaticArrayNodes() {
        UaObjectNode folder = addFoldersToRoot(cttFolder, "/Static/AllProfiles/Array");

        for (Object[] os : StaticArrayNodes) {
            String name = (String) os[0];
            NodeId typeId = (NodeId) os[1];
            Object value = os[2];
            Object array = Array.newInstance(value.getClass(), 16);
            for (int i = 0; i < 16; i++) {
                Array.set(array, i, value);
            }
            Variant variant = new Variant(array);

            UaVariableNode node = new UaVariableNodeBuilder(this)
                    .setNodeId(new NodeId(namespaceIndex, "/Static/AllProfiles/Array/" + name))
                    .setAccessLevel(ubyte(AccessLevel.getMask(AccessLevel.READ_WRITE)))
                    .setBrowseName(new QualifiedName(namespaceIndex, name))
                    .setDisplayName(LocalizedText.english(name))
                    .setDataType(typeId)
                    .setTypeDefinition(Identifiers.BaseDataVariableType)
                    .setValueRank(ValueRank.OneDimension)
                    .setArrayDimensions(new UInteger[]{uint(0)})
                    .build();

            node.setValue(new DataValue(variant));

            folder.addReference(new Reference(
                    folder.getNodeId(),
                    Identifiers.Organizes,
                    node.getNodeId().expanded(),
                    node.getNodeClass(),
                    true
            ));

            logger.debug("Added reference: {} -> {}", folder.getNodeId(), node.getNodeId());

            nodes.put(node.getNodeId(), node);
        }
    }

    private void addMethodNodes() {
        UaObjectNode folder = addFoldersToRoot(cttFolder, "/Methods");

        UaMethodNode methodNode = UaMethodNode.builder(this)
                .setNodeId(new NodeId(namespaceIndex, "/Methods/sqrt(x)"))
                .setBrowseName(new QualifiedName(namespaceIndex, "sqrt(x)"))
                .setDisplayName(new LocalizedText(null, "sqrt(x)"))
                .setDescription(LocalizedText.english("Returns the correctly rounded positive square root of a double value."))
                .build();

        try {
            AnnotationBasedInvocationHandler invocationHandler =
                    AnnotationBasedInvocationHandler.fromAnnotatedObject(this, new SqrtMethod());

            methodNode.setProperty(UaMethodNode.InputArguments, invocationHandler.getInputArguments());
            methodNode.setProperty(UaMethodNode.OutputArguments, invocationHandler.getOutputArguments());
            methodNode.setInvocationHandler(invocationHandler);

            nodes.put(methodNode.getNodeId(), methodNode);

            folder.addReference(new Reference(
                    folder.getNodeId(),
                    Identifiers.HasComponent,
                    methodNode.getNodeId().expanded(),
                    methodNode.getNodeClass(),
                    true
            ));
        } catch (Exception e) {
            logger.error("Error creating sqrt() method.", e);
        }
    }

    private UaObjectNode addFoldersToRoot(UaNode root, String path) {
        if (path.startsWith("/")) path = path.substring(1, path.length());
        String[] elements = path.split("/");

        LinkedList<UaObjectNode> folderNodes = processPathElements(
                Lists.newArrayList(elements),
                Lists.newArrayList(),
                Lists.newLinkedList()
        );

        UaObjectNode firstNode = folderNodes.getFirst();

        if (!nodes.containsKey(firstNode.getNodeId())) {
            nodes.put(firstNode.getNodeId(), firstNode);

            nodes.get(root.getNodeId()).addReference(new Reference(
                    root.getNodeId(),
                    Identifiers.Organizes,
                    firstNode.getNodeId().expanded(),
                    firstNode.getNodeClass(),
                    true
            ));

            logger.debug("Added reference: {} -> {}", root.getNodeId(), firstNode.getNodeId());
        }

        PeekingIterator<UaObjectNode> iterator = Iterators.peekingIterator(folderNodes.iterator());

        while (iterator.hasNext()) {
            UaObjectNode node = iterator.next();

            nodes.putIfAbsent(node.getNodeId(), node);

            if (iterator.hasNext()) {
                UaObjectNode next = iterator.peek();

                if (!nodes.containsKey(next.getNodeId())) {
                    nodes.put(next.getNodeId(), next);

                    nodes.get(node.getNodeId()).addReference(new Reference(
                            node.getNodeId(),
                            Identifiers.Organizes,
                            next.getNodeId().expanded(),
                            next.getNodeClass(),
                            true
                    ));

                    logger.debug("Added reference: {} -> {}", node.getNodeId(), next.getNodeId());
                }
            }
        }

        return folderNodes.getLast();
    }

    private LinkedList<UaObjectNode> processPathElements(List<String> elements, List<String> path, LinkedList<UaObjectNode> nodes) {
        if (elements.size() == 1) {
            String name = elements.get(0);
            String prefix = String.join("/", path) + "/";
            if (!prefix.startsWith("/")) prefix = "/" + prefix;

            UaObjectNode node = UaObjectNode.builder(this)
                    .setNodeId(new NodeId(namespaceIndex, prefix + name))
                    .setBrowseName(new QualifiedName(namespaceIndex, name))
                    .setDisplayName(LocalizedText.english(name))
                    .setTypeDefinition(Identifiers.FolderType)
                    .build();

            nodes.add(node);

            return nodes;
        } else {
            String name = elements.get(0);
            String prefix = String.join("/", path) + "/";
            if (!prefix.startsWith("/")) prefix = "/" + prefix;

            UaObjectNode node = UaObjectNode.builder(this)
                    .setNodeId(new NodeId(namespaceIndex, prefix + name))
                    .setBrowseName(new QualifiedName(namespaceIndex, name))
                    .setDisplayName(LocalizedText.english(name))
                    .setTypeDefinition(Identifiers.FolderType)
                    .build();

            nodes.add(node);
            path.add(name);

            return processPathElements(elements.subList(1, elements.size()), path, nodes);
        }
    }

    @Override
    public UShort getNamespaceIndex() {
        return namespaceIndex;
    }

    @Override
    public String getNamespaceUri() {
        return NAMESPACE_URI;
    }

    @Override
    public boolean containsNodeId(NodeId nodeId) {
        return nodes.containsKey(nodeId);
    }

    @Override
    public <T> T getAttribute(NodeId nodeId, int attributeId) {
        UaNode node = nodes.get(nodeId);
        if (node != null) {
            try {
                return (T) node.readAttribute(attributeId).getValue().getValue();
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }

    @Override
    public boolean attributeExists(NodeId nodeId, int attributeId) {
        UaNode node = nodes.get(nodeId);
        return node != null && node.hasAttribute(attributeId);
    }

    @Override
    public Optional<List<Reference>> getReferences(NodeId nodeId) {
        UaNode node = nodes.get(nodeId);

        if (node != null) {
            return Optional.of(node.getReferences());
        } else {
            return Optional.empty();
        }
    }

    @Override
    public void addNode(UaNode node) {
        nodes.put(node.getNodeId(), node);
    }

    @Override
    public Optional<UaNode> getNode(NodeId nodeId) {
        return Optional.ofNullable(nodes.get(nodeId));
    }

    @Override
    public Optional<UaNode> getNode(ExpandedNodeId nodeId) {
        return nodeId.local().flatMap(this::getNode);
    }

    @Override
    public Optional<UaNode> removeNode(NodeId nodeId) {
        return Optional.ofNullable(nodes.remove(nodeId));
    }

    @Override
    public void read(List<ReadValueId> readValueIds,
                     Double maxAge,
                     TimestampsToReturn timestamps,
                     CompletableFuture<List<DataValue>> future) {

        List<DataValue> results = Lists.newArrayListWithCapacity(readValueIds.size());

        for (ReadValueId id : readValueIds) {
            UaNode node = nodes.get(id.getNodeId());

            if (node != null) {
                DataValue value = node.readAttribute(
                        id.getAttributeId().intValue(),
                        timestamps,
                        id.getIndexRange()
                );

                if (logger.isTraceEnabled()) {
                    Variant variant = value.getValue();
                    Object o = variant != null ? variant.getValue() : null;
                    logger.trace("Read value={} from attributeId={} of {}",
                            o, id.getAttributeId(), id.getNodeId());
                }

                results.add(value);
            } else {
                results.add(new DataValue(new StatusCode(StatusCodes.Bad_NodeIdInvalid)));
            }
        }

        future.complete(results);
    }

    @Override
    public void write(List<WriteValue> writeValues, CompletableFuture<List<StatusCode>> future) {
        List<StatusCode> results = Lists.newArrayListWithCapacity(writeValues.size());

        for (WriteValue writeValue : writeValues) {
            try {
                UaNode node = Optional.ofNullable(nodes.get(writeValue.getNodeId()))
                        .orElseThrow(() -> new UaException(StatusCodes.Bad_NodeIdUnknown));

                node.writeAttribute(
                        server.getNamespaceManager(),
                        writeValue.getAttributeId().intValue(),
                        writeValue.getValue(),
                        writeValue.getIndexRange()
                );

                if (logger.isTraceEnabled()) {
                    Variant variant = writeValue.getValue().getValue();
                    Object o = variant != null ? variant.getValue() : null;
                    logger.trace("Wrote value={} to attributeId={} of {}",
                            o, writeValue.getAttributeId(), writeValue.getNodeId());
                }

                results.add(StatusCode.GOOD);
            } catch (UaException e) {
                results.add(e.getStatusCode());
            }
        }

        future.complete(results);
    }

    @Override
    public void onDataItemsCreated(List<DataItem> dataItems) {
        subscriptionModel.onDataItemsCreated(dataItems);
    }

    @Override
    public void onDataItemsModified(List<DataItem> dataItems) {
        subscriptionModel.onDataItemsModified(dataItems);
    }

    @Override
    public void onDataItemsDeleted(List<DataItem> dataItems) {
        subscriptionModel.onDataItemsDeleted(dataItems);
    }

    @Override
    public void onMonitoringModeChanged(List<MonitoredItem> monitoredItems) {
        subscriptionModel.onMonitoringModeChanged(monitoredItems);
    }

    @Override
    public Optional<MethodInvocationHandler> getInvocationHandler(NodeId methodId) {
        UaNode node = nodes.get(methodId);

        if (node instanceof UaMethodNode) {
            return ((UaMethodNode) node).getInvocationHandler();
        } else {
            return Optional.empty();
        }
    }

}
