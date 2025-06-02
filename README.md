
/**
 * nested dropdown component
 */
import {  NestedTreeControl } from "@angular/cdk/tree";
import { Component, Input, OnDestroy, ViewChild } from "@angular/core";
import { MatTreeNestedDataSource } from "@angular/material/tree";
import { models } from "powerbi-client";
import { INestedNode } from "src/app/model/countrynode.model";
import { IFilterData } from "src/app/model/filterdata.model";
import { MatCheckbox } from "@angular/material/checkbox";
import { ReportService } from "src/app/service/report.service";
import { Subject, Subscription } from 'rxjs';

@Component({
  selector: "app-nesteddropdown",
  templateUrl: "./nesteddropdown.component.html",
  styleUrl: "./nesteddropdown.component.scss",
  standalone :false
})
export class NesteddropdownComponent implements OnDestroy {
  @Input() fieldName: string;
  @Input() tableName: string;
  @Input() columnName: string;
  private _filterObject: unknown[];
  @Input() operator: string;
  @Input() displayNameRoot: string;
  @Input() displayNameNode: string;
  @ViewChild("toggleAll") toggleAll: MatCheckbox;
  totalValuesLength: number;
  filterData = {} as IFilterData;
  treeData: INestedNode[];
  displayFieldValue: string;
  selectedListValues: string[];
  isShowList = false;
  isAllVisible = true;
  orderByProperty = "name";
  toggleContent = "dropdown-content-hide";
  documentClickSubscription: Subscription | undefined;
  private readonly onDestroy$ = new Subject<void>();
  
  public dataSource = new MatTreeNestedDataSource<INestedNode>();

getChildren = (node: INestedNode): INestedNode[] => node.children || [];

getLevel = (node: INestedNode): number => {
  let level = 0;
  let current = node.parent;
  while (current) {
    level++;
    current = current.parent;
  }
  return level;
};


toggleNode(node: INestedNode): void {
  node.expanded = !node.expanded;
}
  public selectedDataSource = new MatTreeNestedDataSource<INestedNode>();
  public hasChild = (_: number, node: INestedNode) =>
    !!node.children && node.children.length > 0;
  searchInputValue = '';
  constructor(private readonly reportService: ReportService) {
    this.selectedListValues = [];
    this.displayFieldValue = "All";
  }
  ngOnDestroy(): void {
    this.onDestroy$.next();
  }

  @Input()
  set filterObject(values: unknown[]) {
    this._filterObject = values;
    if (this._filterObject) {
      this.groupCountries();
    }
  }

  get filterObject(): unknown[] {
    return this._filterObject;
  }

  showList() {
    this.isShowList = !this.isShowList;
    if (this.isShowList) {
      this.openList();
    } else {
      this.closeList();
    }
  }
  closeList() {
    this.toggleContent = "dropdown-content-hide";
  }
  openList() {
    this.toggleContent = "dropdown-content-show";
  }
  toggleSelectAll() {
    if (this.toggleAll.checked) {
      this.setAllNodesSelected(this.treeData);
      this.selectedDataSource.data = structuredClone(this.treeData);
      this.storeSelectedValue();
    } else {
      this.setAllNodesDeselected(this.treeData);
      this.selectedDataSource.data = structuredClone(this.treeData);
      this.storeSelectedValue();
    }
    this.reportService.setToggleFilter(true);
  }

  setParent(node: INestedNode, parent: INestedNode) {
    node.parent = parent;
    if (node.children) {
      node.children.forEach(childNode => {
        this.setParent(childNode, node);
      });
    }
  }

  getDescendants(node: INestedNode): INestedNode[] 
  { let descendants: INestedNode[] = []; if (node.children) 
    { for (const child of node.children) {
       descendants.push(child); descendants = descendants.concat(this.getDescendants(child)); } }
        return descendants; }

  checkAllParents(node: INestedNode) {
    if (node.parent) {
      const descendants = this.getDescendants(node.parent);
      node.parent.selected = descendants.every(child => child.selected);
      node.parent.indeterminate = descendants.some(child => child.selected);
      this.checkAllParents(node.parent);
    }
  }
  groupCountries() {
    const list = this.filterObject;
    const nodeData = list.reduce((acc, obj) => {
      const name = obj[this.displayNameRoot];
      if (!acc[name]) {
        acc[name] = { name, children: [] };
      }
      if (obj[this.displayNameNode] !== obj[this.displayNameRoot]) {
        acc[name].children.push({
          name: obj[this.displayNameNode],
        });
      }
      return acc;
    }, {});
    this.treeData = Object.values(nodeData);
    this.treeData = this.sortTree(this.treeData);
    Object.keys(this.treeData).forEach(key => {
      this.setParent(this.treeData[key], null);
    });
    this.setAllNodesDeselected(this.treeData);
    this.dataSource.data = this.treeData;
    this.selectedDataSource.data = structuredClone(this.treeData);
    this.totalValuesLength = this.getNodesCount();
    this.storeSelectedValue();
  }

  getNodesCount() {
    const result = this.selectedDataSource.data.reduce(
      (acc: INestedNode[], node: INestedNode) =>
        acc.concat(
          this
            .getDescendants(node)
            .filter(descendant => !descendant.selected)
        ),
      [] as INestedNode[]
    );
    const names = result.map(x => x.name);
    return names.length;
  }
  sortTree(nodes: INestedNode[]): INestedNode[] {
    return nodes
      .map(node => {
        if (node.children) {
          node.children = this.sortTree(node.children);
        }
        return node;
      })
      .sort((a, b) => a.name.localeCompare(b.name));
  }
  setAllNodesSelected(nodes: INestedNode[]): void {
    nodes.forEach(node => {
      node.selected = true;
      node.indeterminate = true;
      if (node.children) {
        this.setAllNodesSelected(node.children);
      }
    });
  }
  setAllNodesDeselected(nodes: INestedNode[]): void {
    nodes.forEach(node => {
      node.selected = false;
      node.indeterminate = false;
      if (node.children) {
        this.setAllNodesDeselected(node.children);
      }
    });
  }
  valueToggle(checked: boolean, node: INestedNode) {
    this.itemToggle(checked, node);
    this.storeSelectedValue();
    this.checkAll();
    this.reportService.setToggleFilter(true);
  }
  resetValues() {
    if (this.toggleAll) {
      this.toggleAll.checked = false;
    }
    this.setAllNodesDeselected(this.treeData);
    this.resetSelection();
    this.storeSelectedValue();
    this.searchInputValue = '';
    this.filterOptions(this.searchInputValue);
  }
  resetSelection() {
    this.selectedDataSource.data.forEach(node =>
      this.resetNodeSelection(node)
    );
  }

  resetNodeSelection(node: INestedNode) {
    node.selected = false;
    node.indeterminate = false;
    if (node.children) {
      node.children.forEach(child => this.resetNodeSelection(child));
    }
  }
  storeSelectedValue() {
    const result = this.selectedDataSource.data.reduce(
      (acc: INestedNode[], node: INestedNode) =>
        acc.concat(
          this
            .getDescendants(node)
            .filter(descendant => descendant.selected)
        ),
      [] as INestedNode[]
    );
    this.selectedListValues = result.map(x => x.name);
    if (this.selectedListValues.length === 1) {
      this.displayFieldValue = this.selectedListValues[0];
    } else if (
      this.selectedListValues.length > 1 &&
      this.selectedListValues.length < this.totalValuesLength
    ) {
      this.displayFieldValue = "Multiple Selections";
    } else if (this.selectedListValues.length === this.totalValuesLength) {
      this.displayFieldValue = "All";
    } else {
      this.displayFieldValue = "All";
    }
    this.getSelectedValue();
  }
  getSelectedValue() {
    this.filterData = {
      Table: this.tableName,
      Column: this.columnName,
      Operator: this.operator as models.BasicFilterOperators,
      SelectedValues: this.selectedListValues,
    };
    return this.filterData;
  }

  itemToggle(checked: boolean, node: INestedNode) {
    node.selected = checked;
    node.indeterminate = checked;
    if (node.children) {
      node.children.forEach(child => {
        this.itemToggle(checked, child);
      });
    }
    this.checkAllParents(node);
  }
  checkAll() {
    if (this.toggleAll) {
      if (this.selectedListValues.length === this.totalValuesLength) {
        this.toggleAll.checked = true;
      } else {
        this.toggleAll.checked = false;
      }
    }
  }
  filterOptions(searchValue: string) {
    const filterValue = searchValue.toLowerCase();
    if (!filterValue) {
      this.selectedDataSource.data = this.treeData;
    }
    this.selectedDataSource.data = this.treeData
      .map(node => this.filterNode(node, filterValue))
      .filter(Boolean);
    if (this.selectedDataSource.data.length === this.treeData.length) {
      this.isAllVisible = true;
    } else {
      this.isAllVisible = false;
    }
  }

  filterNode(node: INestedNode, filterValue: string): INestedNode | null {
    const children = node.children
      ?.map(child => this.filterNode(child, filterValue))
      .filter(Boolean);
    const isVisible =
      node.name.toLowerCase().includes(filterValue) ||
      (children && children.length > 0);
    if (isVisible) {
      return { ...node };
    }
    return null;
  }
}
