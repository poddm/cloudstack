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
package org.apache.cloudstack.vmstateupdate;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import org.apache.cloudstack.api.command.admin.vm.UpdateVmStateCmd;
import org.springframework.stereotype.Component;

import com.cloud.utils.component.ComponentLifecycleBase;
import com.cloud.vm.VMInstanceVO;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.dao.VMInstanceDao;

@Component
public class UpdateVmStateServiceImpl extends ComponentLifecycleBase implements UpdateVmStateService {

    @Inject
    private VMInstanceDao _vmInstanceDao;

    @Override
    public boolean updateVmState(Long vmId, String state) throws IllegalArgumentException {
        VirtualMachine.State newState;
        try {
            newState = VirtualMachine.State.valueOf(state);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid state: " + state +
                ". Valid values are: Starting, Running, Stopping, Stopped, Destroyed, Expunging, Migrating, Error, Unknown, Shutdown, Restoring");
        }

        VMInstanceVO vm = _vmInstanceDao.findById(vmId);
        if (vm == null) {
            throw new IllegalArgumentException("Virtual machine with ID " + vmId + " not found");
        }

        vm.setState(newState);
        return _vmInstanceDao.update(vmId, vm);
    }

    @Override
    public List<Class<?>> getCommands() {
        List<Class<?>> cmdList = new ArrayList<>();
        cmdList.add(UpdateVmStateCmd.class);
        return cmdList;
    }
}
