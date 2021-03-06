package org.apache.spark.rqg.ast

import org.apache.spark.rqg.DataType

/**
 * All TreeNode is generated by an companion object class which extends one of generators.
 *
 * This `generator` class is used to make sure TreeNode generator has same apply function and
 * we can easily random choose a generator from an Array. For example:
 *
 * val choices = Array(ConstantGenerator, ColumnGenerator, ExpressionGenerator)
 * RandomUtil.choose(choices).apply(querySession, parent)
 */
trait Generator[T]

/**
 * Common Generator trait, most TreeNode generator extends this
 */
trait TreeNodeGenerator[T] extends Generator[T] {
  def apply(querySession: QueryContext, parent: Option[TreeNode]): T
}

/**
 * SubQuery needs to have a return type. Therefore, NestedQuery and Select Clause
 * should extends this class
 */
trait TreeNodeWithParent[T] extends Generator[T] {
  def apply(querySession: QueryContext, parent: Option[TreeNode], requiredDataType: Option[DataType[_]]): T
}

/**
 * All kinds of RelationPrimary generator extends this, such as:
 * TableReference, AliasedQuery, FunctionTable. For now, we only support TableReference
 */
trait RelationPrimaryGenerator[T] extends Generator[T] {
  def apply(querySession: QueryContext, parent: Option[TreeNode]): T
}

/**
 * All kinds of Expression generator extends this, such as:
 * BooleanExpression, ValueExpression, PrimaryExpression. This generator has more parameters than
 * others.
 */
trait ExpressionGenerator[T] extends Generator[T] {
  /**
   * If a generator is possible to generate a primitive expression, like Constant, ColumnReference,
   * then return true. For example, ValueExpression -> PrimaryExpression -> Constant (return true)
   * LogicalNot can only generate "NOT xxx" which is nested expression (return false)
   */
  def canGeneratePrimitive: Boolean

  /**
   * If a generator is possible to generate a relational expression, like comparison, return true.
   */
  def canGenerateRelational: Boolean

  /**
   *  If a generator is possible to generate a aggregation function. like count, sum
   */
  def canGenerateAggFunc: Boolean

  /**
   * If a generator is possible to generat a nested expression, like LogicalBinary, return true.
   * For example, ColumnReference should return false
   */
  def canGenerateNested: Boolean

  /**
   * possible data type a generator can return. For example: Comparison can only return BooleanType
   */
  def possibleDataTypes(queryContext: QueryContext): Array[DataType[_]]

  /**
   * @param requiredDataType means the generated expression should return this data type
   * @param isLast means if this is the last expression in a nested expression tree.
   *               This parameter used to make sure we can generate enough nested expression
   *               For example, in below case, when generating expr2, isLast = true. and if we need
   *               more nested expression, expr2 can't be a primitive one
   *                          AND
   *                         /   \
   *                     expr1  expr2
   */
  def apply(
      querySession: QueryContext,
      parent: Option[TreeNode],
      requiredDataType: DataType[_],
      isLast: Boolean): T
}

/**
 * Similar to ExpressionGenerator, but requiredDataType is not a return type since predicate is
 * not actually an expression
 */
trait PredicateGenerator[T] extends Generator[T] {
  def apply(querySession: QueryContext, parent: Option[TreeNode], requiredDataType: DataType[_]): T
}
