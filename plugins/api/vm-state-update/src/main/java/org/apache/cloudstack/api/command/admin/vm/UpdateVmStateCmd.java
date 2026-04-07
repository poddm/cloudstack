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
package org.apache.cloudstack.api.command.admin.vm;

import javax.inject.Inject;

import org.apache.cloudstack.acl.RoleType;
import org.apache.cloudstack.api.ACL;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.response.SuccessResponse;
import org.apache.cloudstack.api.response.UserVmResponse;
import org.apache.cloudstack.vmstateupdate.UpdateVmStateService;

import com.cloud.user.Account;
import com.cloud.vm.VirtualMachine;

@APICommand(name = "updateVirtualMachineState",
            description = "Updates the state of a virtual machine. This is an admin-only operation.",
            responseObject = SuccessResponse.class,
            entityType = {VirtualMachine.class},
            requestHasSensitiveInfo = false,
            responseHasSensitiveInfo = false,
            authorized = {RoleType.Admin})
public class UpdateVmStateCmd extends BaseCmd {

    @ACL
    @Parameter(name = ApiConstants.ID,
               type = CommandType.UUID,
               entityType = UserVmResponse.class,
               required = true,
               description = "The ID of the virtual machine")
    private Long id;

    @Parameter(name = ApiConstants.STATE,
               type = CommandType.STRING,
               required = true,
               description = "The state to set for the virtual machine. Valid values are: Starting, Running, Stopping, Stopped, Destroyed, Expunging, Migrating, Error, Unknown, Shutdown, Restoring")
    private String state;

    @Inject
    UpdateVmStateService _updateVmStateService;

    public Long getId() {
        return id;
    }

    public String getState() {
        return state;
    }

    @Override
    public void execute() throws ServerApiException {
        try {
            boolean result = _updateVmStateService.updateVmState(getId(), getState());
            if (result) {
                SuccessResponse response = new SuccessResponse(getCommandName());
                response.setDisplayText("Virtual machine state updated successfully to " + getState());
                setResponseObject(response);
            } else {
                throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to update virtual machine state");
            }
        } catch (IllegalArgumentException e) {
            throw new ServerApiException(ApiErrorCode.PARAM_ERROR, e.getMessage());
        }
    }

    @Override
    public long getEntityOwnerId() {
        return Account.ACCOUNT_ID_SYSTEM;
    }
}
