package com.ruleforge.console.service;

import com.ruleforge.console.entity.DeploymentConfigEntity;
import com.ruleforge.console.entity.ExecutorNodeEntity;

import java.util.List;

/**
 * Service for managing multi-node deployment configurations.
 * Tracks which package versions are deployed to which executor nodes.
 */
public interface DeploymentService {

    /**
     * Register a new executor node.
     */
    ExecutorNodeEntity registerNode(String nodeName, String nodeUrl, String execEnv);

    /**
     * List all executor nodes, optionally filtered by environment.
     */
    List<ExecutorNodeEntity> listNodes(String execEnv);

    /**
     * Update a node's heartbeat (called by executor).
     */
    void updateHeartbeat(Long nodeId);

    /**
     * Deploy a package version to a specific executor node (or all nodes in an env).
     *
     * @param projectId     project ID
     * @param packageId     package identifier
     * @param gitTag        Git tag for the version
     * @param version       human-readable version number
     * @param execEnv       target environment
     * @param executorNodeId specific node ID, or null for all nodes in env
     * @param deployUser    user who triggered the deployment
     * @return the created deployment config entity
     */
    DeploymentConfigEntity deploy(Long projectId, String packageId, String gitTag,
                                   String version, String execEnv,
                                   Long executorNodeId, String deployUser);

    /**
     * Get the current deployment for a package in an environment.
     */
    DeploymentConfigEntity getCurrentDeployment(Long projectId, String packageId, String execEnv);

    /**
     * Get all deployments for a package across all environments.
     */
    List<DeploymentConfigEntity> getDeployments(Long projectId, String packageId);

    /**
     * Roll back a package to a previous version.
     */
    DeploymentConfigEntity rollback(Long projectId, String packageId,
                                     String targetGitTag, String targetVersion,
                                     String execEnv, String deployUser);

    /**
     * Deploy a package version to all executor nodes in a specific node group.
     *
     * @param projectId  project ID
     * @param packageId  package identifier
     * @param gitTag     Git tag for the version
     * @param version    human-readable version number
     * @param execEnv    target environment
     * @param nodeGroup  target node group (e.g., "canary")
     * @param deployUser user who triggered the deployment
     * @return list of created deployment config entities
     */
    List<DeploymentConfigEntity> deployToGroup(Long projectId, String packageId, String gitTag,
                                                String version, String execEnv,
                                                String nodeGroup, String deployUser);
}
