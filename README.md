getDescendants(node: INestedNode): INestedNode[] {
  let descendants: INestedNode[] = [];
  if (node.children) {
    for (const child of node.children) {
      descendants.push(child);
      descendants = descendants.concat(this.getDescendants(child));
    }
  }
  return descendants;
}
