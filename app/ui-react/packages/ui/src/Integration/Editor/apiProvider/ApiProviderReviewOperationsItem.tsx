import * as H from '@syndesis/history';
import classNames from 'classnames';
import { ListView } from 'patternfly-react';
import * as React from 'react';
import { ButtonLink } from '../../../Layout';

import './ApiProviderReviewOperationsItem.css';

export interface IApiProviderReviewOperationsItemProps {
  createFlowHref: H.LocationDescriptor;
  i18nCreateFlow: string;
  onCreateFlow?: (e: React.MouseEvent<any>) => void;
  operationDescription: string;
  operationHttpMethod: string;
  operationPath: string;
}

export class ApiProviderReviewOperationsItem extends React.Component<
  IApiProviderReviewOperationsItemProps
> {
  public render() {
    const httpMethodClass = classNames({
      'operation-delete': this.props.operationHttpMethod === 'DELETE',
      'operation-get': this.props.operationHttpMethod === 'GET',
      'operation-post': this.props.operationHttpMethod === 'POST',
      'operation-put': this.props.operationHttpMethod === 'PUT',
    });
    return (
      <ListView.Item
        actions={
          <ButtonLink
            data-testid={'api-provider-operations-create-flow'}
            onClick={this.props.onCreateFlow}
            href={this.props.createFlowHref}
            className={'wizard-pf-back'}
          >
            {this.props.i18nCreateFlow}
          </ButtonLink>
        }
        additionalInfo={this.props.operationPath}
        className={'api-provider-operations-list-item'}
        description={this.props.operationDescription}
        leftContent={
          <span className={httpMethodClass}>
            {this.props.operationHttpMethod}
          </span>
        }
        stacked={false}
      />
    );
  }
}
