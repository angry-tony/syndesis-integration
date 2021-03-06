import { ApplicationRef, Component, Input, OnChanges, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { ModalDirective } from 'ngx-bootstrap/modal';
import { Subscription } from 'rxjs/Subscription';

import {
  Action,
  ActionConfig,
  ListConfig,
  ListEvent,
  EmptyStateConfig,
  Notification,
  NotificationType
} from 'patternfly-ng';

import { Integrations, Integration } from '@syndesis/ui/model';
import { IntegrationStore, IntegrationSupportService } from '@syndesis/ui/store';
import { IntegrationViewBase } from '../components';
import { ModalService, NotificationService } from '@syndesis/ui/common';
import { log, getCategory } from '@syndesis/ui/logging';

interface ActionConfigs {
  [id: string]: ActionConfig;
}

@Component({
  selector: 'syndesis-integrations-list',
  templateUrl: './list.component.html',
  styleUrls: ['./list.component.scss']
})
export class IntegrationsListComponent extends IntegrationViewBase implements OnInit, OnChanges {
  @Input() complete: boolean;
  @Input() integrations: Integrations = [];
  listConfig: ListConfig;
  actionConfigs: ActionConfigs;

  constructor(
    public store: IntegrationStore,
    public route: ActivatedRoute,
    public router: Router,
    public notificationService: NotificationService,
    public modalService: ModalService,
    public application: ApplicationRef,
    integrationSupportService: IntegrationSupportService,
  ) {
    super(store, route, router, notificationService, modalService, application, integrationSupportService);
  }

  handleAction($event: Action, item: any) {
    if ($event.id === 'createIntegration') {
      this.router.navigate(['/integrations/create']);
    }
  }

  handleClick($event: ListEvent) {
    this.router.navigate(['/integrations', $event.item.id], {
      relativeTo: this.route
    });
  }

  ngOnChanges(changes: any) {
    if (changes.integrations) {
      this.actionConfigs = this.generateActionConfigMap(changes.integrations.currentValue);
    }
  }

  ngOnInit() {
    this.listConfig = {
      dblClick: false,
      multiSelect: false,
      selectItems: false,
      showCheckbox: false,
      emptyStateConfig: {
        iconStyleClass: 'pficon pficon-add-circle-o',
        title: 'Create an integration',
        info:
          'There are currently no integrations available. Please click on the button below to create one.',
        actions: {
          primaryActions: [
            {
              id: 'createIntegration',
              title: 'Create Integration',
              tooltip: 'create an integration'
            }
          ],
          moreActions: []
        } as ActionConfig
      } as EmptyStateConfig
    };
    this.actionConfigs = this.generateActionConfigMap(this.integrations);
  }

  private generateActionConfigMap(integrations: Integrations) {
    const answer: ActionConfigs = {};
    for (const integration of integrations) {
      const actionConfig = this.getActionConfig(integration);
      answer[integration.id] = actionConfig;
    }
    return answer;
  }

  private getActionConfig(integration: Integration): ActionConfig {
    const actionConfig = {
      primaryActions: [],
      moreActions: [
        {
          id: 'view',
          title: 'View',
          tooltip: `View ${integration.name}`,
          visible: true
        },
        {
          id: 'edit',
          title: 'Edit',
          tooltip: `Edit ${integration.name}`,
          visible: this.canEdit(integration)
        },
        {
          id: 'activate',
          title: 'Activate',
          tooltip: `Activate ${integration.name}`,
          visible: this.canActivate(integration)
        },
        {
          id: 'deactivate',
          title: 'Deactivate',
          tooltip: `Deactivate ${integration.name}`,
          visible: this.canDeactivate(integration)
        },
        {
          id: 'delete',
          title: 'Delete',
          tooltip: `Delete ${integration.name}`,
          visible: this.canDelete(integration)
        },
        {
          id: 'export',
          title: 'Export',
          tooltip: `Export ${integration.name}`,
          visible: true
        },
      ],
      moreActionsDisabled: false,
      moreActionsVisible: true
    } as ActionConfig;

    // Hide kebab
    if (integration.currentStatus === 'Deleted') {
      actionConfig.moreActionsVisible = false;
    }

    return actionConfig;
  }
}
