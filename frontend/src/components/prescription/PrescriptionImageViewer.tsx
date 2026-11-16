import { useEffect, useState } from 'react';
import { ImageOff } from 'lucide-react';
import { prescriptionApi } from '@/api/prescription.api';
import { Skeleton } from '@/components/ui/skeleton';

interface PrescriptionImageViewerProps {
  prescriptionId: string;
  alt?: string;
  className?: string;
}

export function PrescriptionImageViewer({
  prescriptionId,
  alt = 'Ảnh đơn thuốc',
  className = 'max-h-[420px] w-full rounded-lg border border-[#E2E8F0] object-contain bg-[#F8FAFC]',
}: PrescriptionImageViewerProps) {
  const [objectUrl, setObjectUrl] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [hasError, setHasError] = useState<boolean>(false);

  useEffect(() => {
    let active = true;
    let createdUrl: string | null = null;

    setIsLoading(true);
    setHasError(false);
    setObjectUrl(null);

    prescriptionApi
      .getPrescriptionImageBlob(prescriptionId)
      .then((blob) => {
        if (!active) return;
        createdUrl = URL.createObjectURL(blob);
        setObjectUrl(createdUrl);
        setIsLoading(false);
      })
      .catch(() => {
        if (!active) return;
        setHasError(true);
        setIsLoading(false);
      });

    return () => {
      active = false;
      if (createdUrl) {
        URL.revokeObjectURL(createdUrl);
      }
    };
  }, [prescriptionId]);

  if (isLoading) {
    return <Skeleton className="h-64 w-full rounded-lg" />;
  }

  if (hasError || !objectUrl) {
    return (
      <div className="flex h-48 flex-col items-center justify-center gap-2 rounded-lg border border-dashed border-[#E2E8F0] bg-[#F8FAFC] p-4 text-center text-xs text-[#475569]">
        <ImageOff className="h-7 w-7 text-[#475569]" />
        <span>Không thể tải ảnh đơn thuốc từ MinIO. Hãy kiểm tra dịch vụ prescription-service / MinIO.</span>
      </div>
    );
  }

  return <img src={objectUrl} alt={alt} className={className} />;
}
