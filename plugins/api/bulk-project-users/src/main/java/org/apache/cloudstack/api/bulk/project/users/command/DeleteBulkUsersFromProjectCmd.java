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

package org.apache.cloudstack.api.bulk.project.users.command;

import java.util.List;

import javax.inject.Inject;

import org.apache.cloudstack.acl.RoleType;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiCommandResourceType;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.BaseAsyncCmd;
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.bulk.project.users.BulkProjectUsersService;
import org.apache.cloudstack.api.response.ProjectResponse;
import org.apache.cloudstack.api.response.SuccessResponse;
import org.apache.cloudstack.api.response.UserResponse;
import org.apache.cloudstack.context.CallContext;

import com.cloud.event.EventTypes;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.projects.Project;

@APICommand(name = "deleteBulkUsersFromProject", description = "Deletes multiple users from a project in a single bulk operation", responseObject = SuccessResponse.class, since = "4.20",
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false,
        authorized = {RoleType.Admin, RoleType.DomainAdmin, RoleType.ResourceAdmin, RoleType.User})
public class DeleteBulkUsersFromProjectCmd extends BaseAsyncCmd {

    @Inject
    private BulkProjectUsersService bulkProjectUsersService;

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////

    @Parameter(name = ApiConstants.PROJECT_ID,
            type = BaseCmd.CommandType.UUID,
            entityType = ProjectResponse.class,
            required = true,
            description = "ID of the project to remove the users from")
    private Long projectId;

    @Parameter(name = "userids",
            type = CommandType.LIST,
            collectionType = CommandType.UUID,
            entityType = UserResponse.class,
            required = true,
            description = "Comma-separated list of user IDs to be removed from the project")
    private List<Long> userIds;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    public Long getProjectId() {
        return projectId;
    }

    public List<Long> getUserIds() {
        return userIds;
    }

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    @Override
    public String getEventType() {
        return EventTypes.EVENT_PROJECT_USER_REMOVE;
    }

    @Override
    public String getEventDescription() {
        return "Removing users " + userIds + " from project: " + getResourceUuid(ApiConstants.PROJECT_ID);
    }

    @Override
    public long getEntityOwnerId() {
        Project project = _projectService.getProject(projectId);
        if (project == null) {
            throw new InvalidParameterValueException("Unable to find project by ID " + projectId);
        }
        return _projectService.getProjectOwner(projectId).getId();
    }

    @Override
    public List<Long> getEntityOwnerIds() {
        return _projectService.getProjectOwners(projectId);
    }

    @Override
    public Long getApiResourceId() {
        return projectId;
    }

    @Override
    public ApiCommandResourceType getApiResourceType() {
        return ApiCommandResourceType.Project;
    }

    @Override
    public void execute() {
        if (userIds == null || userIds.isEmpty()) {
            throw new InvalidParameterValueException("Must specify at least one user ID");
        }
        CallContext.current().setEventDetails("Project ID: " + getResourceUuid(ApiConstants.PROJECT_ID) + "; User IDs: " + userIds);
        boolean result = bulkProjectUsersService.deleteBulkUsersFromProject(getProjectId(), getUserIds());
        if (result) {
            SuccessResponse response = new SuccessResponse(getCommandName());
            this.setResponseObject(response);
        } else {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to delete users from the project");
        }
    }
}
