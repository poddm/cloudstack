// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package org.apache.cloudstack.api.bulk.project.users;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import org.apache.cloudstack.acl.ProjectRole;
import org.apache.cloudstack.acl.SecurityChecker.AccessType;
import org.apache.cloudstack.acl.dao.ProjectRoleDao;
import org.apache.cloudstack.api.bulk.project.users.command.AddBulkUsersToProjectCmd;
import org.apache.cloudstack.api.bulk.project.users.command.DeleteBulkUsersFromProjectCmd;
import org.apache.cloudstack.context.CallContext;

import com.cloud.exception.InvalidParameterValueException;
import com.cloud.projects.Project;
import com.cloud.projects.Project.State;
import com.cloud.projects.ProjectAccount;
import com.cloud.projects.ProjectAccount.Role;
import com.cloud.projects.ProjectAccountVO;
import com.cloud.projects.dao.ProjectAccountDao;
import com.cloud.projects.dao.ProjectDao;
import com.cloud.user.Account;
import com.cloud.user.AccountManager;
import com.cloud.user.User;
import com.cloud.user.dao.AccountDao;
import com.cloud.user.dao.UserDao;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.component.PluggableService;
import com.cloud.utils.db.TransactionLegacy;
import com.cloud.utils.exception.CloudRuntimeException;

public class BulkProjectUsersServiceImpl extends ManagerBase implements BulkProjectUsersService, PluggableService {

    @Inject
    private ProjectDao projectDao;
    @Inject
    private ProjectAccountDao projectAccountDao;
    @Inject
    private UserDao userDao;
    @Inject
    private AccountDao accountDao;
    @Inject
    private AccountManager accountMgr;
    @Inject
    private ProjectRoleDao projectRoleDao;

    @Override
    public List<Class<?>> getCommands() {
        List<Class<?>> cmdList = new ArrayList<>();
        cmdList.add(AddBulkUsersToProjectCmd.class);
        cmdList.add(DeleteBulkUsersFromProjectCmd.class);
        return cmdList;
    }

    @Override
    public boolean addBulkUsersToProject(Long projectId, List<String> usernames, Long projectRoleId, Role projectRole) {
        Account caller = CallContext.current().getCallingAccount();

        Project project = projectDao.findById(projectId);
        if (project == null) {
            InvalidParameterValueException ex = new InvalidParameterValueException("Unable to find project with specified id");
            ex.addProxyObject(String.valueOf(projectId), "projectId");
            throw ex;
        }

        if (project.getState() != State.Active) {
            InvalidParameterValueException ex =
                    new InvalidParameterValueException("Can't add users to project in state=" + project.getState() + " as it isn't currently active");
            ex.addProxyObject(project.getUuid(), "projectId");
            throw ex;
        }

        CallContext.current().setProject(project);
        accountMgr.checkAccess(caller, AccessType.ModifyProject, true, accountMgr.getAccount(project.getProjectAccountId()));

        if (projectRoleId != null && projectRoleId < 1L) {
            throw new InvalidParameterValueException("Invalid project role id provided");
        }

        ProjectRole role = null;
        if (projectRoleId != null) {
            role = projectRoleDao.findById(projectRoleId);
            if (role == null || !role.getProjectId().equals(projectId)) {
                throw new InvalidParameterValueException("Invalid project role ID for the given project");
            }
        }

        List<ProjectAccountVO> projectAccountsToInsert = new ArrayList<>();
        for (String username : usernames) {
            User user = userDao.getUserByName(username, project.getDomainId());
            if (user == null) {
                throw new InvalidParameterValueException("Invalid username provided: " + username);
            }

            // Skip if user is already in the project
            ProjectAccount existingProjectAccount = projectAccountDao.findByProjectIdUserId(projectId, user.getAccountId(), user.getId());
            if (existingProjectAccount != null) {
                logger.info("User: {} is already added to project: {}, skipping", username, project);
                continue;
            }

            Role effectiveRole = projectRole != null ? projectRole : Role.Regular;
            Long effectiveProjectRoleId = role != null ? role.getId() : null;
            ProjectAccountVO projectAccountVO = new ProjectAccountVO(project, user.getAccountId(), effectiveRole, user.getId(), effectiveProjectRoleId);
            projectAccountsToInsert.add(projectAccountVO);
        }

        if (projectAccountsToInsert.isEmpty()) {
            logger.info("All specified users are already in project: {}", project);
            return true;
        }

        batchInsertProjectAccounts(projectAccountsToInsert);
        logger.info("Successfully added {} users to project: {}", projectAccountsToInsert.size(), project);
        return true;
    }

    @Override
    public boolean deleteBulkUsersFromProject(long projectId, List<Long> userIds) {
        Account caller = CallContext.current().getCallingAccount();

        Project project = projectDao.findById(projectId);
        if (project == null) {
            InvalidParameterValueException ex = new InvalidParameterValueException("Unable to find project with specified id");
            ex.addProxyObject(String.valueOf(projectId), "projectId");
            throw ex;
        }

        CallContext.current().setProject(project);
        accountMgr.checkAccess(caller, AccessType.ModifyProject, true, accountMgr.getAccount(project.getProjectAccountId()));

        // Validate all user IDs exist
        for (Long userId : userIds) {
            User user = userDao.findById(userId);
            if (user == null) {
                throw new InvalidParameterValueException("Invalid user ID provided: " + userId);
            }
        }

        int removed = batchDeleteProjectUsers(projectId, userIds);
        logger.info("Successfully removed {} users from project: {}", removed, project);
        return removed > 0;
    }

    /**
     * Performs a single batch INSERT of multiple project_account rows.
     */
    private void batchInsertProjectAccounts(List<ProjectAccountVO> projectAccounts) {
        TransactionLegacy txn = TransactionLegacy.currentTxn();
        StringBuilder sql = new StringBuilder(
                "INSERT INTO project_account (project_id, account_id, user_id, account_role, project_account_id, project_role_id, created) VALUES ");
        for (int i = 0; i < projectAccounts.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append("(?, ?, ?, ?, ?, ?, NOW())");
        }
        try {
            txn.start();
            PreparedStatement pstmt = txn.prepareAutoCloseStatement(sql.toString());
            int index = 1;
            for (ProjectAccountVO pa : projectAccounts) {
                pstmt.setLong(index++, pa.getProjectId());
                pstmt.setLong(index++, pa.getAccountId());
                if (pa.getUserId() != null) {
                    pstmt.setLong(index++, pa.getUserId());
                } else {
                    pstmt.setNull(index++, java.sql.Types.BIGINT);
                }
                pstmt.setString(index++, pa.getAccountRole().toString());
                pstmt.setLong(index++, pa.getProjectAccountId());
                if (pa.getProjectRoleId() != null) {
                    pstmt.setLong(index++, pa.getProjectRoleId());
                } else {
                    pstmt.setNull(index++, java.sql.Types.BIGINT);
                }
            }
            pstmt.executeUpdate();
            txn.commit();
        } catch (SQLException e) {
            txn.rollback();
            throw new CloudRuntimeException("Failed to batch insert project accounts", e);
        }
    }

    /**
     * Performs a single batch DELETE of multiple users from a project.
     */
    private int batchDeleteProjectUsers(long projectId, List<Long> userIds) {
        TransactionLegacy txn = TransactionLegacy.currentTxn();
        StringBuilder sql = new StringBuilder("DELETE FROM project_account WHERE project_id = ? AND user_id IN (");
        for (int i = 0; i < userIds.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append("?");
        }
        sql.append(")");
        try {
            txn.start();
            PreparedStatement pstmt = txn.prepareAutoCloseStatement(sql.toString());
            int index = 1;
            pstmt.setLong(index++, projectId);
            for (Long userId : userIds) {
                pstmt.setLong(index++, userId);
            }
            int removed = pstmt.executeUpdate();
            txn.commit();
            return removed;
        } catch (SQLException e) {
            txn.rollback();
            throw new CloudRuntimeException("Failed to batch delete project accounts", e);
        }
    }
}
