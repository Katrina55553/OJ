/* generated using openapi-typescript-codegen -- do no edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { BaseResponse_boolean_ } from "../models/BaseResponse_boolean_";
import type { BaseResponse_long_ } from "../models/BaseResponse_long_";
import type { BaseResponse_Page_Question_ } from "../models/BaseResponse_Page_Question_";
import type { BaseResponse_Page_QuestionSubmitVO_ } from "../models/BaseResponse_Page_QuestionSubmitVO_";
import type { BaseResponse_Page_QuestionVO_ } from "../models/BaseResponse_Page_QuestionVO_";
import type { BaseResponse_Question_ } from "../models/BaseResponse_Question_";
import type { BaseResponse_QuestionVO_ } from "../models/BaseResponse_QuestionVO_";
import type { DeleteRequest } from "../models/DeleteRequest";
import type { QuestionAddRequest } from "../models/QuestionAddRequest";
import type { QuestionEditRequest } from "../models/QuestionEditRequest";
import type { QuestionQueryRequest } from "../models/QuestionQueryRequest";
import type { QuestionSubmitAddRequest } from "../models/QuestionSubmitAddRequest";
import type { QuestionSubmitQueryRequest } from "../models/QuestionSubmitQueryRequest";
import type { QuestionUpdateRequest } from "../models/QuestionUpdateRequest";

import type { CancelablePromise } from "../core/CancelablePromise";
import { OpenAPI } from "../core/OpenAPI";
import { request as __request } from "../core/request";

export class QuestionControllerService {
  /**
   * addQuestion
   * @description 添加题目
   * @param questionAddRequest 题目添加请求参数
   * @returns BaseResponse_long_ 返回题目ID的响应
   * @returns any 创建成功
   * @throws ApiError
   */
  public static addQuestionUsingPost(
    questionAddRequest: QuestionAddRequest
  ): CancelablePromise<BaseResponse_long_ | any> {
    return __request(OpenAPI, {
      method: "POST",
      url: "/api/question/add",
      body: questionAddRequest,
      errors: {
        401: `未授权`,
        403: `禁止访问`,
        404: `资源未找到`,
      },
    });
  }

  /**
   * deleteQuestion
   * @description 删除题目
   * @param deleteRequest 删除请求参数
   * @returns BaseResponse_boolean_ 返回删除结果的响应
   * @returns any 创建成功
   * @throws ApiError
   */
  public static deleteQuestionUsingPost(
    deleteRequest: DeleteRequest
  ): CancelablePromise<BaseResponse_boolean_ | any> {
    return __request(OpenAPI, {
      method: "POST",
      url: "/api/question/delete",
      body: deleteRequest,
      errors: {
        401: `未授权`,
        403: `禁止访问`,
        404: `资源未找到`,
      },
    });
  }

  /**
   * editQuestion
   * @description 编辑题目
   * @param questionEditRequest 题目编辑请求参数
   * @returns BaseResponse_boolean_ 返回编辑结果的响应
   * @returns any 创建成功
   * @throws ApiError
   */
  public static editQuestionUsingPost(
    questionEditRequest: QuestionEditRequest
  ): CancelablePromise<BaseResponse_boolean_ | any> {
    return __request(OpenAPI, {
      method: "POST",
      url: "/api/question/edit",
      body: questionEditRequest,
      errors: {
        401: `未授权`,
        403: `禁止访问`,
        404: `资源未找到`,
      },
    });
  }

  /**
   * getQuestionById
   * @description 根据ID获取题目详情（管理端）
   * @param id 题目ID
   * @returns BaseResponse_Question_ 返回题目完整信息的响应
   * @throws ApiError
   */
  public static getQuestionByIdUsingGet(
    id?: number
  ): CancelablePromise<BaseResponse_Question_> {
    return __request(OpenAPI, {
      method: "GET",
      url: "/api/question/get",
      query: {
        id: id,
      },
      errors: {
        401: `未授权`,
        403: `禁止访问`,
        404: `资源未找到`,
      },
    });
  }

  /**
   * getQuestionVOById
   * @description 根据ID获取题目VO（用户端）
   * @param id 题目ID
   * @returns BaseResponse_QuestionVO_ 返回题目视图对象的响应
   * @throws ApiError
   */
  public static getQuestionVoByIdUsingGet(
    id?: number
  ): CancelablePromise<BaseResponse_QuestionVO_> {
    return __request(OpenAPI, {
      method: "GET",
      url: "/api/question/get/vo",
      query: {
        id: id,
      },
      errors: {
        401: `未授权`,
        403: `禁止访问`,
        404: `资源未找到`,
      },
    });
  }

  /**
   * listQuestionByPage
   * @description 分页查询题目列表（管理端）
   * @param questionQueryRequest 题目查询请求参数
   * @returns BaseResponse_Page_Question_ 返回题目分页列表的响应
   * @returns any 创建成功
   * @throws ApiError
   */
  public static listQuestionByPageUsingPost(
    questionQueryRequest: QuestionQueryRequest
  ): CancelablePromise<BaseResponse_Page_Question_ | any> {
    return __request(OpenAPI, {
      method: "POST",
      url: "/api/question/list/page",
      body: questionQueryRequest,
      errors: {
        401: `未授权`,
        403: `禁止访问`,
        404: `资源未找到`,
      },
    });
  }

  /**
   * listQuestionVOByPage
   * @description 分页查询题目VO列表（用户端）
   * @param questionQueryRequest 题目查询请求参数
   * @returns BaseResponse_Page_QuestionVO_ 返回题目VO分页列表的响应
   * @returns any 创建成功
   * @throws ApiError
   */
  public static listQuestionVoByPageUsingPost(
    questionQueryRequest: QuestionQueryRequest
  ): CancelablePromise<BaseResponse_Page_QuestionVO_ | any> {
    return __request(OpenAPI, {
      method: "POST",
      url: "/api/question/list/page/vo",
      body: questionQueryRequest,
      errors: {
        401: `未授权`,
        403: `禁止访问`,
        404: `资源未找到`,
      },
    });
  }

  /**
   * listMyQuestionVOByPage
   * @description 分页查询当前用户的题目VO列表
   * @param questionQueryRequest 题目查询请求参数
   * @returns BaseResponse_Page_QuestionVO_ 返回用户题目VO分页列表的响应
   * @returns any 创建成功
   * @throws ApiError
   */
  public static listMyQuestionVoByPageUsingPost(
    questionQueryRequest: QuestionQueryRequest
  ): CancelablePromise<BaseResponse_Page_QuestionVO_ | any> {
    return __request(OpenAPI, {
      method: "POST",
      url: "/api/question/my/list/page/vo",
      body: questionQueryRequest,
      errors: {
        401: `未授权`,
        403: `禁止访问`,
        404: `资源未找到`,
      },
    });
  }

  /**
   * doQuestionSubmit
   * @description 提交题目答案
   * @param questionSubmitAddRequest 题目提交请求参数
   * @returns BaseResponse_long_ 返回提交记录ID的响应
   * @returns any 创建成功
   * @throws ApiError
   */
  public static doQuestionSubmitUsingPost(
    questionSubmitAddRequest: QuestionSubmitAddRequest
  ): CancelablePromise<BaseResponse_long_ | any> {
    return __request(OpenAPI, {
      method: "POST",
      url: "/api/question/question_submit/do",
      body: questionSubmitAddRequest,
      errors: {
        401: `未授权`,
        403: `禁止访问`,
        404: `资源未找到`,
      },
    });
  }

  /**
   * listQuestionSubmitByPage
   * @description 分页查询题目提交记录
   * @param questionSubmitQueryRequest 题目提交查询请求参数
   * @returns BaseResponse_Page_QuestionSubmitVO_ 返回题目提交记录分页列表的响应
   * @returns any 创建成功
   * @throws ApiError
   */
  public static listQuestionSubmitByPageUsingPost(
    questionSubmitQueryRequest: QuestionSubmitQueryRequest
  ): CancelablePromise<BaseResponse_Page_QuestionSubmitVO_ | any> {
    return __request(OpenAPI, {
      method: "POST",
      url: "/api/question/question_submit/list/page",
      body: questionSubmitQueryRequest,
      errors: {
        401: `未授权`,
        403: `禁止访问`,
        404: `资源未找到`,
      },
    });
  }

  /**
   * updateQuestion
   * @description 更新题目信息
   * @param questionUpdateRequest 题目更新请求参数
   * @returns BaseResponse_boolean_ 返回更新结果的响应
   * @returns any 创建成功
   * @throws ApiError
   */
  public static updateQuestionUsingPost(
    questionUpdateRequest: QuestionUpdateRequest
  ): CancelablePromise<BaseResponse_boolean_ | any> {
    return __request(OpenAPI, {
      method: "POST",
      url: "/api/question/update",
      body: questionUpdateRequest,
      errors: {
        401: `未授权`,
        403: `禁止访问`,
        404: `资源未找到`,
      },
    });
  }
}
