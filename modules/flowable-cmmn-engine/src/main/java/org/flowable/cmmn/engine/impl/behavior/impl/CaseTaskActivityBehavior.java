/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.flowable.cmmn.engine.impl.behavior.impl;

import org.flowable.cmmn.engine.PlanItemInstanceCallbackType;
import org.flowable.cmmn.engine.impl.behavior.PlanItemActivityBehavior;
import org.flowable.cmmn.engine.impl.persistence.entity.CaseInstanceEntity;
import org.flowable.cmmn.engine.impl.persistence.entity.PlanItemInstanceEntity;
import org.flowable.cmmn.engine.impl.runtime.CaseInstanceHelper;
import org.flowable.cmmn.engine.impl.util.CommandContextUtil;
import org.flowable.cmmn.engine.runtime.DelegatePlanItemInstance;
import org.flowable.cmmn.engine.runtime.PlanItemInstanceState;
import org.flowable.cmmn.model.PlanItemTransition;
import org.flowable.engine.common.api.FlowableException;
import org.flowable.engine.common.impl.interceptor.CommandContext;

/**
 * @author Joram Barrez
 */
public class CaseTaskActivityBehavior extends TaskActivityBehavior implements PlanItemActivityBehavior {
    
    protected String caseRef;
    
    public CaseTaskActivityBehavior(String caseRef, boolean isBlocking) {
        super(isBlocking);
        this.caseRef = caseRef;
    }

    @Override
    public void execute(DelegatePlanItemInstance planItemInstance) {
        CommandContext commandContext = CommandContextUtil.getCommandContext();
        
        CaseInstanceHelper caseInstanceHelper = CommandContextUtil.getCaseInstanceHelper(commandContext);
        CaseInstanceEntity caseInstanceEntity = caseInstanceHelper.startCaseInstanceByKey(commandContext, caseRef);
        caseInstanceEntity.setParentId(planItemInstance.getCaseInstanceId());
        
        // Bidirectional storing of reference to avoid queries later on
        caseInstanceEntity.setCallbackType(PlanItemInstanceCallbackType.CHILD_CASE);
        caseInstanceEntity.setCallbackId(planItemInstance.getId());
        
        planItemInstance.setReferenceType(PlanItemInstanceCallbackType.CHILD_CASE);
        planItemInstance.setReferenceId(caseInstanceEntity.getId());
        
        if (!isBlocking) {
            CommandContextUtil.getAgenda(commandContext).planCompletePlanItem((PlanItemInstanceEntity) planItemInstance);
        }
    }
    
    @Override
    public void trigger(DelegatePlanItemInstance planItemInstance) {
        if (isBlocking) {
            
            if (!PlanItemInstanceState.ACTIVE.equals(planItemInstance.getState())) {
                throw new FlowableException("Can only trigger a plan item that is in the ACTIVE state");
            }
            if (planItemInstance.getReferenceId() == null) {
                throw new FlowableException("Cannot trigger case task plan item instance : no reference id set");
            }
            if (!PlanItemInstanceCallbackType.CHILD_CASE.equals(planItemInstance.getReferenceType())) {
                throw new FlowableException("Cannot trigger case task plan item instance : reference type '" 
                        + planItemInstance.getReferenceType() + "' not supported");
            }
            
            // Triggering the plan item (as opposed to a regular complete) terminates the case instance
            CommandContext commandContext = CommandContextUtil.getCommandContext();
            CommandContextUtil.getAgenda(commandContext).planTerminateCase(planItemInstance.getReferenceId(), true);
            CommandContextUtil.getAgenda(commandContext).planCompletePlanItem((PlanItemInstanceEntity) planItemInstance);
        }
    }

    @Override
    public void onStateTransition(DelegatePlanItemInstance planItemInstance, String transition) {
        if (PlanItemTransition.TERMINATE.equals(transition) || PlanItemTransition.EXIT.equals(transition)) {
            // The plan item will be deleted by the regular TerminatePlanItemOperation
            CommandContextUtil.getAgenda().planTerminateCase(planItemInstance.getReferenceId(), true);
        }
    }
    
}
