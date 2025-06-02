<mat-tree [dataSource]="selectedDataSource" class="example-tree">

  <!-- Leaf node (no children) -->
  <mat-tree-node *ngFor="let node of selectedDataSource.data | orderBy: orderByProperty">
    <ul class="parent">
      <li class="mat-tree-node">
        <div matTooltip="{{ node.name }}" matTooltipPosition="after">
          <mat-checkbox
            disableRipple
            class="checklist-leaf-node"
            (change)="valueToggle($event.checked, node)"
            [checked]="node.selected"
          >
            <span class="node-label">{{ node.name | titlecase }}</span>
          </mat-checkbox>
        </div>
      </li>
    </ul>
  </mat-tree-node>

  <!-- Nested node -->

  <mat-nested-tree-node *matTreeNodeDef="let node; when: hasChild">
    <ul class="parent">
      <li>
        <div class="mat-tree-node">
          <button
            mat-icon-button
            (click)="node.expanded = !node.expanded"
            [attr.aria-label]="'toggle ' + node.name"
          >
            <mat-icon class="mat-icon-rtl-mirror">
              {{ node.expanded ? "expand_more" : "chevron_right" }}
            </mat-icon>
          </button>

          <div matTooltip="{{ node.name }}" matTooltipPosition="after">
            <mat-checkbox
              disableRipple
              [checked]="node.selected"
              [indeterminate]="node.indeterminate && !node.selected"
              (change)="valueToggle($event.checked, node)"
            >
              <span class="node-label">{{ node.name | titlecase }}</span>
            </mat-checkbox>
          </div>
        </div>

        <ul [class.example-tree-invisible]="!node.expanded">
          <ng-container matTreeNodeOutlet></ng-container>
        </ul>
      </li>
    </ul>
  </mat-nested-tree-node>

</mat-tree>
