import { httpClient } from './httpClient';
import type {
  ApprovePrescriptionRequest,
  PrescriptionPageResponse,
  PrescriptionResponse,
  PrescriptionStatus,
  RejectPrescriptionRequest,
} from '@/types/prescription.types';

export const prescriptionApi = {
  async uploadPrescription(file: File, notes?: string): Promise<PrescriptionResponse> {
    const formData = new FormData();
    formData.append('image', file);
    if (notes && notes.trim().length > 0) {
      formData.append('notes', notes.trim());
    }

    const response = await httpClient.post<PrescriptionResponse>('/v1/prescriptions', formData);
    return response.data;
  },

  async getMyPrescriptions(params: {
    status?: PrescriptionStatus | '';
    page?: number;
    size?: number;
  }): Promise<PrescriptionPageResponse> {
    const query: Record<string, string | number> = {
      page: params.page ?? 0,
      size: params.size ?? 20,
    };
    if (params.status) {
      query.status = params.status;
    }
    const response = await httpClient.get<PrescriptionPageResponse>(
      '/v1/prescriptions/my-prescriptions',
      { params: query },
    );
    return response.data;
  },

  async getAllPrescriptions(params: {
    status?: PrescriptionStatus | '';
    page?: number;
    size?: number;
  }): Promise<PrescriptionPageResponse> {
    const query: Record<string, string | number> = {
      page: params.page ?? 0,
      size: params.size ?? 20,
    };
    if (params.status) {
      query.status = params.status;
    }
    const response = await httpClient.get<PrescriptionPageResponse>('/v1/prescriptions', {
      params: query,
    });
    return response.data;
  },

  async getPendingPrescriptions(params: {
    page?: number;
    size?: number;
  }): Promise<PrescriptionPageResponse> {
    const response = await httpClient.get<PrescriptionPageResponse>(
      '/v1/prescriptions/pending',
      {
        params: {
          page: params.page ?? 0,
          size: params.size ?? 20,
        },
      },
    );
    return response.data;
  },

  async getPrescriptionById(id: string): Promise<PrescriptionResponse> {
    const response = await httpClient.get<PrescriptionResponse>(`/v1/prescriptions/${id}`);
    return response.data;
  },

  async getPrescriptionImageBlob(id: string): Promise<Blob> {
    const response = await httpClient.get<Blob>(`/v1/prescriptions/${id}/image`, {
      responseType: 'blob',
    });
    return response.data;
  },

  async approvePrescription(
    id: string,
    payload: ApprovePrescriptionRequest,
  ): Promise<PrescriptionResponse> {
    const response = await httpClient.put<PrescriptionResponse>(
      `/v1/prescriptions/${id}/approve`,
      payload,
    );
    return response.data;
  },

  async rejectPrescription(
    id: string,
    payload: RejectPrescriptionRequest,
  ): Promise<PrescriptionResponse> {
    const response = await httpClient.put<PrescriptionResponse>(
      `/v1/prescriptions/${id}/reject`,
      payload,
    );
    return response.data;
  },
};
